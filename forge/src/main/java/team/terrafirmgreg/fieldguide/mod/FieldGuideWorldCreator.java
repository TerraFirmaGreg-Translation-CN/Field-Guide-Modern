package team.terrafirmgreg.fieldguide.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPresets;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Programmatically creates and loads the dedicated {@code guide-export} world used by the
 * auto-export pipeline so CI / local runs never have to ship a save template.
 *
 * <p>The world is:</p>
 * <ul>
 *   <li>Superflat with the vanilla {@code the_void} preset (single AIR layer + the_void biome),
 *       so nothing actually generates and chunk loading is instant.</li>
 *   <li>Creative game mode, Peaceful difficulty, cheats on, fixed seed 0.</li>
 *   <li>GameRules tuned for headless determinism: no daylight / weather cycle, no mob spawning,
 *       no fire tick, no mob griefing, keep inventory, {@code randomTickSpeed=0}.</li>
 * </ul>
 *
 * <p>After the server starts {@link FieldGuideWorldServerEvents} places a single bedrock at
 * {@link #BEDROCK_POS} and teleports the player on top of it with creative flight, so we never
 * void-death and we don't need a feature/decoration step.</p>
 */
public final class FieldGuideWorldCreator {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");

    /** Name (= save folder) of the world the export runs in. */
    public static final String SAVE_NAME = "guide-export";

    /** The single bedrock that gives the player something to stand on. */
    public static final BlockPos BEDROCK_POS = new BlockPos(0, 64, 0);

    /** Where the player is teleported (centered on top of the bedrock). */
    public static final BlockPos PLAYER_SPAWN_POS = new BlockPos(0, 65, 0);

    private FieldGuideWorldCreator() {}

    /**
     * {@code true} if {@code <gameDir>/saves/guide-export/} already exists.
     * Auto-export reuses this folder on later runs (local disk or CI cache) to skip world creation.
     */
    public static boolean saveExists(Minecraft mc) {
        try {
            return mc.getLevelSource().levelExists(SAVE_NAME);
        } catch (Exception e) {
            LOGGER.warn("levelExists({}) threw; assuming missing", SAVE_NAME, e);
            return false;
        }
    }

    /**
     * Open an existing save by name (used when the save was created on a previous run).
     * The {@code parent} screen is shown if loading fails (vanilla returns the user there).
     */
    public static void openExisting(Minecraft mc) {
        LOGGER.info("opening existing world '{}'", SAVE_NAME);
        mc.createWorldOpenFlows().loadLevel(mc.screen, SAVE_NAME);
    }

    /**
     * Asynchronously create a fresh creative void world and load it. Returns immediately; the
     * client transitions to LoadingOverlay → in-world on the main thread.
     */
    public static void createAndLoad(Minecraft mc) {
        LOGGER.info("creating fresh void creative world '{}' (seed=0, peaceful, cheats=on)", SAVE_NAME);

        GameRules rules = buildHeadlessGameRules();

        LevelSettings settings = new LevelSettings(
                SAVE_NAME,
                GameType.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                rules,
                WorldDataConfiguration.DEFAULT
        );

        WorldOptions worldOptions = new WorldOptions(0L, false, false);

        mc.createWorldOpenFlows().createFreshLevel(
                SAVE_NAME,
                settings,
                worldOptions,
                FieldGuideWorldCreator::buildVoidDimensions
        );
    }

    private static GameRules buildHeadlessGameRules() {
        GameRules rules = new GameRules();
        setBool(rules, GameRules.RULE_DAYLIGHT, false);
        setBool(rules, GameRules.RULE_WEATHER_CYCLE, false);
        setBool(rules, GameRules.RULE_DOMOBSPAWNING, false);
        setBool(rules, GameRules.RULE_DOFIRETICK, false);
        setBool(rules, GameRules.RULE_MOBGRIEFING, false);
        setBool(rules, GameRules.RULE_KEEPINVENTORY, true);
        setInt(rules, GameRules.RULE_RANDOMTICKING, 0);
        return rules;
    }

    private static void setBool(GameRules rules, GameRules.Key<GameRules.BooleanValue> key, boolean value) {
        rules.getRule(key).set(value, null);
    }

    private static void setInt(GameRules rules, GameRules.Key<GameRules.IntegerValue> key, int value) {
        rules.getRule(key).set(value, null);
    }

    /**
     * Build {@link WorldDimensions} = vanilla {@code minecraft:normal} preset (overworld + nether + end)
     * with the overworld's {@link ChunkGenerator} swapped to a {@link FlatLevelSource} configured
     * with the built-in {@code the_void} preset settings (single AIR layer, the_void biome).
     *
     * <p>Note: {@code WorldPresets.createNormalWorldDimensions(...)} does not exist in 1.20.1 — we
     * resolve the {@code NORMAL} preset from the registry and call
     * {@link WorldPreset#createWorldDimensions()} instead.</p>
     */
    private static WorldDimensions buildVoidDimensions(RegistryAccess registries) {
        FlatLevelGeneratorSettings voidSettings = registries
                .registryOrThrow(Registries.FLAT_LEVEL_GENERATOR_PRESET)
                .getHolderOrThrow(FlatLevelGeneratorPresets.THE_VOID)
                .value()
                .settings();

        ChunkGenerator voidGen = new FlatLevelSource(voidSettings);

        WorldPreset normal = registries
                .registryOrThrow(Registries.WORLD_PRESET)
                .getHolderOrThrow(WorldPresets.NORMAL)
                .value();
        WorldDimensions normalDims = normal.createWorldDimensions();

        return normalDims.replaceOverworldGenerator(registries, voidGen);
    }
}
