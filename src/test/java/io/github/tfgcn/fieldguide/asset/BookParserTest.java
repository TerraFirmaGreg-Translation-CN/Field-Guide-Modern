package io.github.tfgcn.fieldguide.asset;

import io.github.tfgcn.fieldguide.BookParser;
import io.github.tfgcn.fieldguide.Context;
import io.github.tfgcn.fieldguide.MCMeta;
import io.github.tfgcn.fieldguide.Versions;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * desc:
 *
 * @author yanmaoyuan
 */
public class BookParserTest {

    public static void main(String[] args) throws IOException {
        // The TerraFirmaGreg modpack directory
        String modpackPath = "/Users/yanmaoyuan/HMCL/.minecraft/versions/TerraFirmaGreg-Modern-0.11.7";
        //String modpackPath = "E:\\HMCL-3.6.12\\.minecraft\\versions\\TerraFirmaGreg-Modern-0.11.7";

        modpackPath = modpackPath.replace("\\", "/");

        MCMeta.loadCache(Versions.MC_VERSION, Versions.FORGE_VERSION, Versions.LANGUAGES);

        AssetLoader assetLoader = new AssetLoader(Paths.get(modpackPath));
        assetLoader.addMcClientSource();// Add minecraft client jar manually

        Context context = new Context(assetLoader, "assets/www", "/Field-Guide-TFG", false);
        // Context context = new Context(assetLoader, "output", ".", false);

        BookParser bookParser = new BookParser();
        bookParser.processAllLanguages(context);

        System.out.println("Missing keys: " + context.missingKeys.size());
        for (String key : context.missingKeys) {
            System.out.println(key);
        }
    }
}
