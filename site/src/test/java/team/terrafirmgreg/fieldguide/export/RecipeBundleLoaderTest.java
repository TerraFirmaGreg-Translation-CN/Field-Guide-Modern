package team.terrafirmgreg.fieldguide.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RecipeBundleLoaderTest {

    @Test
    void loadsBundledRecipes(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("data/demo"));
        Files.writeString(root.resolve("data/demo/recipes.json"), """
                {
                  "demo:foo/bar": {
                    "type": "minecraft:crafting_shapeless",
                    "result": { "item": "minecraft:dirt" }
                  }
                }
                """);

        RecipeBundleLoader loader = RecipeBundleLoader.load(root);
        assertEquals(1, loader.size());
        assertTrue(loader.contains("demo:foo/bar"));
        assertEquals("minecraft:crafting_shapeless", loader.get("demo:foo/bar").get("type"));
    }

    @Test
    void emptyWhenNoBundles(@TempDir Path root) {
        RecipeBundleLoader loader = RecipeBundleLoader.load(root);
        assertEquals(0, loader.size());
        assertFalse(loader.contains("demo:foo/bar"));
    }
}
