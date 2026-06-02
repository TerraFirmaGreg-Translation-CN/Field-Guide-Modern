package team.terrafirmgreg.fieldguide.site.emi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmiRecipeIndexTest {

    @TempDir
    Path emiRoot;

    @Test
    void indexesRecipeIdFromMetaField() throws Exception {
        writeBundle(1);
        Path ns = emiRoot.resolve("recipes/sns");
        Files.createDirectories(ns);
        Files.writeString(
                ns.resolve("anvil_metal_horseshoe_steel.json"),
                """
                        {"schema":1,"id":"sns:anvil/metal/horseshoe/steel","width":1,"height":1,"widgets":[]}
                        """);

        EmiRecipeIndex index = EmiRecipeIndex.load(emiRoot);

        assertTrue(index.contains("sns:anvil/metal/horseshoe/steel"));
        assertFalse(index.contains("sns:anvil_metal_horseshoe_steel"));
    }

    @Test
    void indexesRecipeIdFromCardPathWhenMetaOmitsId() throws Exception {
        writeBundle(1);
        Path ns = emiRoot.resolve("recipes/sns");
        Files.createDirectories(ns);
        Files.writeString(
                ns.resolve("anvil_metal_horseshoe_steel.json"),
                """
                        {"schema":1,"width":1,"height":1,"widgets":[]}
                        """);

        EmiRecipeIndex index = EmiRecipeIndex.load(emiRoot);

        assertTrue(index.contains("sns:anvil/metal/horseshoe/steel"));
    }

    private void writeBundle(int recipeCount) throws Exception {
        Files.writeString(
                emiRoot.resolve("bundle.json"),
                """
                        {"schema":2,"recipeCount":%d,"languages":["en_us"],"missingIconId":"minecraft:barrier"}
                        """.formatted(recipeCount));
    }
}
