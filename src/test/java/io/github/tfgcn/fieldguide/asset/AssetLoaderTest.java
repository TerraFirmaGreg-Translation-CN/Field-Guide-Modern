package io.github.tfgcn.fieldguide.asset;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * desc:
 *
 * @author yanmaoyuan
 */
public class AssetLoaderTest {

    @Test
    void test() {
        Assertions.assertTrue(true);
    }

    public static void main(String[] args) throws IOException {
        Path path = Paths.get("/Users/yanmaoyuan/HMCL/.minecraft/versions/TerraFirmaGreg-Modern-0.11.7");
        AssetLoader assetLoader = new AssetLoader(path);
        assetLoader.generateSourceReport();

        List<Asset> assets = assetLoader.listAssets("assets/tfc/patchouli_books/field_guide/en_us/categories");
        System.out.println("Total assets: " + assets.size());
        for (Asset asset : assets) {
            System.out.println(asset);
        }
    }
}
