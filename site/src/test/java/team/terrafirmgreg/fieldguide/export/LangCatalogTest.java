package team.terrafirmgreg.fieldguide.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmgreg.fieldguide.localization.Language;
import team.terrafirmgreg.fieldguide.site.ExportLocalizationManager;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LangCatalogTest {

    @Test
    void loadsLocalesAndTranslates(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("lang"));
        Files.writeString(root.resolve("lang/en_us.json"), """
                { "patchouli.tfg.title": "Field Guide" }
                """);
        Files.writeString(root.resolve("lang/zh_cn.json"), """
                { "patchouli.tfg.title": "实地指南" }
                """);

        LangCatalog catalog = LangCatalog.load(root);
        assertTrue(catalog.getLanguages().contains(Language.EN_US));
        assertTrue(catalog.getLanguages().contains(Language.ZH_CN));

        ExportLocalizationManager l10n = new ExportLocalizationManager(catalog);
        l10n.switchLanguage(Language.ZH_CN);
        assertEquals("实地指南", l10n.translate("patchouli.tfg.title"));
    }
}
