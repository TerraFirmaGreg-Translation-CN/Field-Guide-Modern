package team.terrafirmgreg.fieldguide.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server-side setup for the {@code guide-export} world: place the single bedrock platform
 * and put the joining player on top of it with creative flight + invulnerability.
 *
 * <p>Active only when {@code -Dfieldguide.runExportAndExit=true} (the CI / export driver path).
 * In normal dev/runClient sessions this class is a no-op so we don't surprise players who
 * happen to name a save {@code guide-export}.</p>
 */
@Mod.EventBusSubscriber(modid = FieldGuideMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FieldGuideWorldServerEvents {

    private FieldGuideWorldServerEvents() {}

    private static boolean enabled() {
        return Boolean.getBoolean("fieldguide.runExportAndExit");
    }

    /** Fires after the integrated server is fully started but before the client connects. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!enabled()) {
            return;
        }
        ServerLevel overworld = event.getServer().overworld();
        if (overworld == null) {
            FieldGuideMod.LOGGER.warn("ServerStartedEvent: overworld is null, skipping bedrock setup");
            return;
        }

        BlockPos bedrock = FieldGuideWorldCreator.BEDROCK_POS;
        overworld.setBlock(bedrock, Blocks.BEDROCK.defaultBlockState(), 3);
        overworld.setDefaultSpawnPos(FieldGuideWorldCreator.PLAYER_SPAWN_POS, 0f);

        FieldGuideMod.LOGGER.info("placed single bedrock at {} and set spawn at {} ({})",
                bedrock,
                FieldGuideWorldCreator.PLAYER_SPAWN_POS,
                overworld.dimension().location());
    }

    /** Fires after a player joins the level (in singleplayer this is the local player). */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel overworld = player.server.overworld();
        if (overworld == null) {
            FieldGuideMod.LOGGER.warn("PlayerLoggedInEvent: overworld is null, skipping teleport");
            return;
        }

        BlockPos spawn = FieldGuideWorldCreator.PLAYER_SPAWN_POS;
        player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0f, 0f);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.getAbilities().invulnerable = true;
        player.onUpdateAbilities();

        FieldGuideMod.LOGGER.info("teleported {} to {} with creative flight + invulnerability",
                player.getName().getString(), spawn);
    }
}
