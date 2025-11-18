package io.github.tfgcn.fieldguide.asset;

import io.github.tfgcn.fieldguide.Language;
import io.github.tfgcn.fieldguide.data.patchouli.Book;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.github.tfgcn.fieldguide.Constants.FIELD_GUIDE;

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
            Book localizedBook = loader.loadBook(FIELD_GUIDE, lang.getKey(), book);
            localizedBook.report();
        }
    }

}
