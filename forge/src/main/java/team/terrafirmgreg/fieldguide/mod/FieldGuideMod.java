package team.terrafirmgreg.fieldguide.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Forge harness for the field-guide export pipeline.
 *
 * <h3>Auto-export modes (client only)</h3>
 * <ul>
 *   <li>{@code -Dfieldguide.runExportAndExit=true} — once idle on a menu screen, this mod
 *       programmatically creates (or reopens) the dedicated {@code guide-export} save
 *       (creative void world, single bedrock at spawn — see {@link FieldGuideWorldCreator}),
 *       waits for player + level, warms up, runs {@code /fieldguide export} and exits.
 *       <strong>Do NOT pass {@code --quickPlaySingleplayer} — the mod owns world loading.</strong>
 *       If the launcher already passes {@code --username} (e.g. HeadlessMC), do not repeat it in {@code --game-args}.
 *       Optional: {@code -Dfieldguide.exportWorldDelayTicks=600} menu wait before first world create (default 30s; skipped when reusing save),
 *       {@code -Dfieldguide.exportWarmupTicks=2400} extra ticks after spawn before export (default 2 min @ 20 TPS; KubeJS),
 *       {@code -Dfieldguide.exportTimeoutSeconds=10800} hard timeout for the entire run (default 7200s, &lt;=0 disables).
 *       Reuses {@code saves/guide-export/} when present ({@link FieldGuideWorldCreator#SAVE_NAME}).
 *       Auto-fails on fatal menu screens ({@code LoadingErrorScreen}, {@code KubeJSErrorScreen}, etc.).</li>
 *   <li>{@code -Dfieldguide.exportAtTitleAndExit=true} — stub export when idle outside a world (any menu screen).
 *       Faster smoke test only; does not enter a world (recipes / GT MaterialSet / KubeJS server scripts not available).</li>
 *   <li>In-game: {@code /fieldguide export}</li>
 * </ul>
 */
@Mod(FieldGuideMod.MOD_ID)
public class FieldGuideMod {

    public static final String MOD_ID = "fieldguide";

    static final Logger LOGGER = LogManager.getLogger("fieldguide");

    /** Tick count between "still waiting" heartbeat lines (~10s @ 20 TPS). */
    private static final int HEARTBEAT_TICKS = 200;

    /** Default ticks to wait in-world before export (~2 minutes @ 20 TPS for KubeJS / GT recipe sync). */
    private static final int DEFAULT_EXPORT_WARMUP_TICKS = 2400;

    /**
     * Ticks to wait on the title/menu screen before creating a <b>new</b> world (Moonlight runtime packs,
     * KubeJS startup, etc.). Opening an existing {@code guide-export} save skips this delay.
     */
    private static final int DEFAULT_EXPORT_WORLD_DELAY_TICKS = 600;

    /**
     * Default hard timeout for the entire auto-export run; &lt;=0 disables.
     * First integrated-server start on a heavy modpack (KubeJS + GTCEu + recipe sync)
     * routinely exceeds 5 minutes on CI; full closure export with icon/recipe bakes can exceed 30 minutes.
     */
    private static final int DEFAULT_EXPORT_TIMEOUT_SECONDS = 7200;

    private static int exportWarmupTicks() {
        return Integer.getInteger("fieldguide.exportWarmupTicks", DEFAULT_EXPORT_WARMUP_TICKS);
    }

    private static int exportWorldDelayTicks() {
        return Integer.getInteger("fieldguide.exportWorldDelayTicks", DEFAULT_EXPORT_WORLD_DELAY_TICKS);
    }

    private static int exportTimeoutSeconds() {
        return Integer.getInteger("fieldguide.exportTimeoutSeconds", DEFAULT_EXPORT_TIMEOUT_SECONDS);
    }

    /**
     * Screens that mean auto-export must abort (no world create, no stub export).
     * Matched by simple class name to avoid compile-time deps on Forge / KubeJS GUI types.
     */
    private static boolean isFatalMenuScreen(Minecraft client) {
        if (client.screen == null) {
            return false;
        }
        String simple = client.screen.getClass().getSimpleName();
        return switch (simple) {
            case "LoadingErrorScreen", "ErrorScreen", "KubeJSErrorScreen", "DisconnectedScreen" -> true;
            default -> false;
        };
    }

    /**
     * {@code true} when the client is idle on a normal menu (not loading overlay, not an error UI).
     */
    private static boolean isIdleMenuReady(Minecraft client) {
        if (client.getOverlay() instanceof LoadingOverlay) {
            return false;
        }
        if (client.screen == null || client.level != null || client.player != null) {
            return false;
        }
        return !isFatalMenuScreen(client);
    }

    private static boolean timedOut(long startNanos) {
        int sec = exportTimeoutSeconds();
        if (sec <= 0) {
            return false;
        }
        return (System.nanoTime() - startNanos) >= sec * 1_000_000_000L;
    }

    public FieldGuideMod() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            LOGGER.info("server dist detected, auto-export handlers disabled");
            return;
        }
        if (Boolean.getBoolean("fieldguide.exportAtTitleAndExit")) {
            LOGGER.info("mode=exportAtTitleAndExit, exportDir={}, warmupTicks={}",
                    exportDir(), exportWarmupTicks());
            MinecraftForge.EVENT_BUS.register(new AutoExportOnTitleScreen());
        } else if (Boolean.getBoolean("fieldguide.runExportAndExit")) {
            LOGGER.info("mode=runExportAndExit, exportDir={}, warmupTicks={}",
                    exportDir(), exportWarmupTicks());
            MinecraftForge.EVENT_BUS.register(new AutoExportOnClientReady());
        } else {
            LOGGER.info("mode=manual (use /fieldguide export in-game)");
        }
    }

    private static Path exportDir() {
        return Paths.get(System.getProperty("fieldguide.exportFolder", "build/guide-export"));
    }

    /**
     * Logs the first time a state is entered, then every {@link #HEARTBEAT_TICKS} ticks
     * for as long as that same state persists.
     */
    private static final class StateLogger {
        private String state = "";
        private int sameStateTicks;

        void tick(String newState) {
            if (!newState.equals(state)) {
                state = newState;
                sameStateTicks = 0;
                LOGGER.info(newState);
            } else if (++sameStateTicks % HEARTBEAT_TICKS == 0) {
                LOGGER.info("{} (still waiting, {} ticks)", newState, sameStateTicks);
            }
        }
    }

    /**
     * Post-load mode: emit stub manifest and exit as soon as the client is idle outside any world.
     *
     * <p>We intentionally do NOT check for {@code TitleScreen} — modpacks often replace it
     * (FancyMenu / DrippyLoadingScreen / CustomLoadingScreen) or pop a first-launch modal,
     * which would never be an instance of vanilla {@code TitleScreen}.</p>
     *
     * <p>Trigger conditions (every condition must hold for {@link #exportWarmupTicks()} ticks):</p>
     * <ul>
     *   <li>No {@link LoadingOverlay} (resource reload finished)</li>
     *   <li>Not in a world ({@code client.level == null && client.player == null})</li>
     *   <li>{@link Minecraft#getInstance()} has a screen attached (we're idle, not still bootstrapping)</li>
     * </ul>
     */
    private static final class AutoExportOnTitleScreen {
        private final StateLogger stateLog = new StateLogger();
        private boolean armed;
        private boolean finished;
        private int idleTicks;
        private long startNanos;

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || finished) {
                return;
            }
            if (!armed) {
                armed = true;
                startNanos = System.nanoTime();
                LOGGER.info("exportAtTitleAndExit: tick handler armed (timeout={}s), waiting for idle menu screen...",
                        exportTimeoutSeconds());
            }
            Minecraft client = Minecraft.getInstance();
            if (isFatalMenuScreen(client)) {
                finished = true;
                LOGGER.error("fatal menu screen ({}); aborting export. Check kubejs/startup_scripts and logs/latest.log.",
                        client.screen.getClass().getName());
                System.exit(1);
                return;
            }
            if (timedOut(startNanos)) {
                finished = true;
                String screen = client.screen == null ? "null" : client.screen.getClass().getName();
                LOGGER.error("export timed out after {}s in exportAtTitleAndExit (screen={})",
                        exportTimeoutSeconds(), screen);
                System.exit(1);
                return;
            }
            if (!isIdleMenuReady(client)) {
                idleTicks = 0;
                if (client.getOverlay() instanceof LoadingOverlay) {
                    stateLog.tick("waiting: resource reload in progress (LoadingOverlay)");
                } else if (client.level != null || client.player != null) {
                    stateLog.tick("waiting: already in a world, not the expected state");
                } else if (client.screen == null) {
                    stateLog.tick("waiting: no screen yet (client still bootstrapping)");
                } else {
                    stateLog.tick("waiting: menu not ready (screen=" + client.screen.getClass().getSimpleName() + ")");
                }
                return;
            }
            String screenName = client.screen.getClass().getName();
            if (++idleTicks == 1) {
                LOGGER.info("idle menu detected (screen={}), counting {} ticks...",
                        screenName, exportWarmupTicks());
            } else if (idleTicks % HEARTBEAT_TICKS == 0) {
                LOGGER.info("idle {}/{} (screen={})", idleTicks, exportWarmupTicks(), screenName);
            }
            if (idleTicks < exportWarmupTicks()) {
                return;
            }
            finished = true;
            try {
                LOGGER.info("Post-load stub export (screen={}, exportDir={}) ...", screenName, exportDir());
                RuntimeExportStub.run(exportDir());
                LOGGER.info("Post-load stub export finished, exiting 0");
                System.exit(0);
            } catch (Exception e) {
                LOGGER.error("Post-load stub export failed", e);
                System.exit(1);
            }
        }
    }

    /**
     * In-world mode: detect idle menu screen → programmatically create/open the
     * {@code guide-export} world → wait for player + warmup → run {@code /fieldguide export} → exit.
     *
     * <p>Phases (a single tick handler advances a small state machine):</p>
     * <ol>
     *   <li>{@code ARMED} — initial; log once.</li>
     *   <li>{@code IDLE_MENU} — overlay gone, no world; trigger {@link FieldGuideWorldCreator}
     *       (create-fresh or open-existing) exactly once.</li>
     *   <li>{@code WORLD_OPENING} — request submitted; waiting for the integrated server to
     *       come up and the local player to spawn.</li>
     *   <li>{@code WARMUP} — player + level present; tick warmup counter.</li>
     *   <li>{@code EXPORTING} — invoke {@link FieldGuideExport#runAsPlayerCommand} and exit.</li>
     * </ol>
     */
    private static final class AutoExportOnClientReady {

        private enum Phase {ARMED, WORLD_OPENING, WARMUP, DONE}

        private final StateLogger stateLog = new StateLogger();
        private Phase phase = Phase.ARMED;
        private boolean worldRequestSent;
        /** Counts menu-screen ticks before {@link FieldGuideWorldCreator#createAndLoad} (new world only). */
        private int worldDelayTicks;
        private int warmupTicks;
        private long startNanos;

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || phase == Phase.DONE) {
                return;
            }
            Minecraft client = Minecraft.getInstance();

            if (phase == Phase.ARMED) {
                startNanos = System.nanoTime();
                phase = Phase.WORLD_OPENING;
                LOGGER.info("runExportAndExit: tick handler armed (timeout={}s), waiting for idle menu...",
                        exportTimeoutSeconds());
            }

            if (isFatalMenuScreen(client)) {
                phase = Phase.DONE;
                LOGGER.error("fatal menu screen ({}); aborting export. KubeJS/modpack must load cleanly before world create.",
                        client.screen.getClass().getName());
                System.exit(1);
                return;
            }
            if (timedOut(startNanos)) {
                String phaseAtTimeout = phase.name();
                phase = Phase.DONE;
                String screen = client.screen == null ? "null" : client.screen.getClass().getName();
                LOGGER.error("export timed out after {}s in runExportAndExit (phase={}, player={}, level={}, screen={})",
                        exportTimeoutSeconds(), phaseAtTimeout,
                        client.player != null, client.level != null, screen);
                System.exit(1);
                return;
            }

            switch (phase) {
                case WORLD_OPENING -> tickWorldOpening(client);
                case WARMUP -> tickWarmup(client);
                default -> {}
            }
        }

        private void tickWorldOpening(Minecraft client) {
            if (client.player != null && client.level != null) {
                phase = Phase.WARMUP;
                LOGGER.info("player + level present (player={}, dim={}), warming up {} ticks",
                        client.player.getName().getString(),
                        client.level.dimension().location(),
                        exportWarmupTicks());
                return;
            }

            // Not in world. Either we haven't requested world load yet, or it's in progress.
            if (!worldRequestSent) {
                if (!isIdleMenuReady(client)) {
                    worldDelayTicks = 0;
                    if (client.getOverlay() instanceof LoadingOverlay) {
                        stateLog.tick("waiting: resource reload in progress (LoadingOverlay)");
                    } else if (client.screen == null) {
                        stateLog.tick("waiting: no screen yet (client still bootstrapping)");
                    } else {
                        stateLog.tick("waiting: menu not ready (screen="
                                + client.screen.getClass().getSimpleName() + ")");
                    }
                    return;
                }
                boolean reuseSave = FieldGuideWorldCreator.saveExists(client);
                int delayTarget = reuseSave ? 0 : exportWorldDelayTicks();
                if (delayTarget > 0 && worldDelayTicks < delayTarget) {
                    worldDelayTicks++;
                    if (worldDelayTicks == 1 || worldDelayTicks % HEARTBEAT_TICKS == 0
                            || worldDelayTicks == delayTarget) {
                        LOGGER.info("world create delay {}/{} ticks (~{}s on idle menu, screen={})",
                                worldDelayTicks, delayTarget, delayTarget / 20,
                                client.screen.getClass().getSimpleName());
                    }
                    return;
                }
                worldRequestSent = true;
                if (reuseSave) {
                    LOGGER.info("save '{}' exists on disk, opening cached world", FieldGuideWorldCreator.SAVE_NAME);
                    FieldGuideWorldCreator.openExisting(client);
                } else {
                    LOGGER.info("save '{}' missing, creating fresh void world (after {} tick delay)",
                            FieldGuideWorldCreator.SAVE_NAME, worldDelayTicks);
                    FieldGuideWorldCreator.createAndLoad(client);
                }
                return;
            }

            String screen = client.screen == null ? "null" : client.screen.getClass().getSimpleName();
            stateLog.tick("waiting: world loading in progress (player=false, level=false, screen=" + screen + ")");
        }

        private void tickWarmup(Minecraft client) {
            if (client.player == null || client.level == null) {
                stateLog.tick("warning: lost player/level during warmup, re-waiting");
                phase = Phase.WORLD_OPENING;
                return;
            }
            if (warmupTicks < exportWarmupTicks()) {
                warmupTicks++;
                if (warmupTicks % HEARTBEAT_TICKS == 0 || warmupTicks == exportWarmupTicks()) {
                    LOGGER.info("warmup {}/{}", warmupTicks, exportWarmupTicks());
                }
                return;
            }
            phase = Phase.DONE;

            LOGGER.info("Running /fieldguide export (exportDir={}) ...", exportDir());
            boolean ok;
            try {
                ok = FieldGuideExport.runAsPlayerCommand(client);
            } catch (Throwable t) {
                LOGGER.error("/fieldguide export threw", t);
                System.exit(1);
                return;
            }
            if (!ok) {
                LOGGER.error("/fieldguide export returned false");
                System.exit(1);
                return;
            }
            LOGGER.info("/fieldguide export finished, exiting 0");
            System.exit(0);
        }
    }
}
