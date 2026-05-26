package team.terrafirmgreg.fieldguide.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MultiblockRegistryTest {

    @Test
    void resolvesRegistryMultiblock(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("meta.json"), """
                {
                  "multiblockDefs": [
                    {
                      "id": "tfc:blast_furnace",
                      "source": "patchouli_registry",
                      "pattern": ["AAA"],
                      "mapping": { "A": "tfc:blast_furnace" }
                    }
                  ]
                }
                """);

        MultiblockRegistry registry = MultiblockRegistry.load(root);
        ResolvedMultiblock mb = registry.resolve("tfc:blast_furnace").orElseThrow();
        assertTrue(mb.isOk());
        assertEquals("patchouli_registry", mb.source());
        assertEquals(List.of("AAA"), mb.pattern());
    }

    @Test
    void resolveFromPageEmbedded() {
        MultiblockRegistry registry = MultiblockRegistry.load(Path.of("nonexistent-dir-missing-meta"));
        ResolvedMultiblock mb = registry.resolveFromPage(
                "custom:page",
                List.of("X"),
                Map.of("X", "minecraft:stone")).orElseThrow();
        assertEquals("page_embedded", mb.source());
    }
}
