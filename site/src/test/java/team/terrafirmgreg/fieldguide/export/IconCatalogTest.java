package team.terrafirmgreg.fieldguide.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class IconCatalogTest {

    @Test
    void resolvesUnifiedIcon(@TempDir Path root) throws Exception {
        Path iconsDir = root.resolve("assets/icons");
        Files.createDirectories(iconsDir);
        Files.writeString(iconsDir.resolve("index.json"), """
                {
                  "schema": 1,
                  "cellSize": 32,
                  "items": {
                    "minecraft:dirt": { "page": 0, "x": 0, "y": 0 }
                  }
                }
                """);

        IconCatalog catalog = IconCatalog.load(root);
        IconRef ref = catalog.resolveItem("minecraft:dirt").orElseThrow();
        assertEquals("icon-atlas", ref.cssClass());
        assertEquals("icons", ref.atlasKind());
        assertEquals("minecraft:dirt", ref.registryId());
        assertEquals(32, ref.cellSize());
        assertEquals(0, ref.x());
    }

    @Test
    void recordsMissingIcons(@TempDir Path root) throws Exception {
        Path iconsDir = root.resolve("assets/icons");
        Files.createDirectories(iconsDir);
        Files.writeString(iconsDir.resolve("index.json"), """
                {
                  "schema": 1,
                  "cellSize": 32,
                  "items": {
                    "fieldguide:missing_icon": { "page": 0, "x": 0, "y": 0 }
                  }
                }
                """);

        MissingIconReport report = new MissingIconReport();
        IconCatalog catalog = IconCatalog.load(root, report);
        IconRef ref = catalog.resolveItem("missing:item").orElseThrow();
        assertEquals(IconCatalog.MISSING_ICON_ID, ref.registryId());
        assertTrue(report.missing().contains("missing:item"));
    }
}
