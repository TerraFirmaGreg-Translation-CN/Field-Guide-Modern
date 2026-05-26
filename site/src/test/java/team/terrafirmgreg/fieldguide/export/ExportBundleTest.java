package team.terrafirmgreg.fieldguide.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ExportBundleTest {

    @Test
    void opensMinimalFixture(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("assets/tfc"));
        Files.writeString(root.resolve("manifest.json"), "{ \"schemaVersion\": \"1.0\" }");
        Files.writeString(root.resolve("meta.json"), "{}");

        ExportBundle bundle = ExportBundle.open(root);
        assertEquals(root, bundle.getExportRoot());
        assertNotNull(bundle.getRecipes());
        assertNotNull(bundle.getAssets());
    }

    @Test
    void rejectsMissingManifest(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("assets"));
        assertThrows(IllegalArgumentException.class, () -> ExportBundle.open(root));
    }

    @Test
    @EnabledIfSystemProperty(named = "guide.export.dir", matches = ".+")
    void opensRealExport() throws Exception {
        Path export = Path.of(System.getProperty("guide.export.dir"));
        ExportBundle bundle = ExportBundle.open(export);
        assertTrue(bundle.getRecipes().size() > 0, "expected recipes in real export");
        assertTrue(Files.isDirectory(bundle.getExportRoot().resolve("generated")));
    }
}
