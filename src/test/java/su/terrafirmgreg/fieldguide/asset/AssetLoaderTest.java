package su.terrafirmgreg.fieldguide.asset;

import su.terrafirmgreg.fieldguide.localization.Language;
import su.terrafirmgreg.fieldguide.data.patchouli.Book;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static su.terrafirmgreg.fieldguide.Constants.FIELD_GUIDE;

@Slf4j
public class AssetLoaderTest {

    static AssetLoader loader;

    @BeforeAll
    static void init() {
        Path path = Paths.get("Modpack-Modern");
        loader = new AssetLoader(path);
    }

    @Test
    void test() {
        Assertions.assertTrue(true);
    }

    @Test
    void testLoadBook() throws IOException {
        Book book = loader.loadBook(FIELD_GUIDE);
        for (Language lang : Language.values()) {
            Book localizedBook = loader.loadBook(FIELD_GUIDE, lang, book);
            Assertions.assertNotNull(localizedBook);
        }
    }

    @Test
    void testLoadRecipe() {
        Map<String, Object> recipe = loader.loadRecipe("tfg:rock_knapping/stone_shovel_head");
        Assertions.assertNotNull(recipe);
    }
}
