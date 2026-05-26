package team.terrafirmgreg.fieldguide.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ExportRecipeLoaderTest {

    @Test
    void loadsBundledRecipes(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("data/demo"));
        Files.writeString(root.resolve("data/demo/recipes.json"), """
                {
                  "demo:foo/bar": {
                    "type": "minecraft:crafting_shapeless",
                    "ingredients": [],
                    "result": { "item": "minecraft:dirt" }
                  }
                }
                """);

        ExportRecipeLoader loader = ExportRecipeLoader.tryLoad(root);
        assertNotNull(loader);
        assertEquals(1, loader.size());
        assertNotNull(loader.get("demo:foo/bar"));
        assertEquals("minecraft:crafting_shapeless", loader.get("demo:foo/bar").get("type"));
    }

    @Test
    void returnsNullWhenNoBundles(@TempDir Path root) {
        assertNull(ExportRecipeLoader.tryLoad(root));
    }
}
