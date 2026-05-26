package team.terrafirmgreg.fieldguide.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Shared logic for {@code /fieldguide export}. */
public final class FieldGuideExport {

    private FieldGuideExport() {}

    public static int run(CommandSourceStack source) {
        try {
            Path output = Paths.get(System.getProperty("fieldguide.exportFolder", "build/guide-export"));
            Component message = RuntimeExportStub.run(output);
            source.sendSystemMessage(message);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("[fieldguide] Export failed: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Invokes the same handler as {@code /fieldguide export} using the local player as command source.
     */
    public static boolean runAsPlayerCommand(Minecraft client) {
        if (client.player == null) {
            return false;
        }
        CommandSourceStack source = client.player.createCommandSourceStack().withPermission(4);
        return run(source) == 1;
    }
}
