package team.terrafirmgreg.fieldguide.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BlockstateRefResolverTest {

    @Test
    void usesOverrideFromMeta(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("meta.json"), """
                {
                  "blockstates": [
                    {
                      "ref": "tfc:charcoal_forge[heat_level=7]",
                      "override": "tfc:charcoal_forge[heat_level=7,is_lit=true]"
                    }
                  ]
                }
                """);
        Files.createDirectories(root.resolve("assets/tfc"));
        Files.writeString(root.resolve("manifest.json"), "{}");

        ExportBundle bundle = ExportBundle.open(root);
        BlockstateRefResolver resolver =
                new BlockstateRefResolver(bundle.getAssets(), bundle.getTagMembers());
        assertEquals(
                "tfc:charcoal_forge[heat_level=7,is_lit=true]",
                resolver.resolveModelId("tfc:charcoal_forge[heat_level=7]"));
    }

    @Test
    void resolvesTagViaTagMembers(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("meta.json"), """
                {
                  "blockstates": [
                    { "ref": "#forge:ores/almandine", "kind": "tag", "tag": "forge:ores/almandine" }
                  ]
                }
                """);
        Files.createDirectories(root.resolve("assets/tfc"));
        Files.writeString(root.resolve("manifest.json"), "{}");
        Files.createDirectories(root.resolve("index"));
        Files.writeString(
                root.resolve("index/tag-members.json"),
                """
                { "blocks": { "forge:ores/almandine": ["gtceu:almandine_ore"] } }
                """);

        ExportBundle bundle = ExportBundle.open(root);
        BlockstateRefResolver resolver =
                new BlockstateRefResolver(bundle.getAssets(), bundle.getTagMembers());
        assertEquals("gtceu:almandine_ore", resolver.resolveModelId("#forge:ores/almandine"));
    }
}
