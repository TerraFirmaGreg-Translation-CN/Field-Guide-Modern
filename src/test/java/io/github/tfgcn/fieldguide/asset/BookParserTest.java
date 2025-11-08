package io.github.tfgcn.fieldguide.asset;

import io.github.tfgcn.fieldguide.BookParser;
import io.github.tfgcn.fieldguide.Context;
import io.github.tfgcn.fieldguide.MCMeta;
import io.github.tfgcn.fieldguide.Versions;

import java.nio.file.Paths;

/**
 * desc:
 *
 * @author yanmaoyuan
 */
public class BookParserTest {

    public static void main(String[] args) {
        //String root = "/Users/yanmaoyuan/HMCL/.minecraft/versions/TerraFirmaGreg-Modern-0.11.7";
        String root = "E:\\HMCL-3.6.12\\.minecraft\\versions\\TerraFirmaGreg-Modern-0.11.7";

        root = root.replace("\\", "/");

        MCMeta.loadCache(Versions.MC_VERSION, Versions.FORGE_VERSION, Versions.LANGUAGES);

        AssetLoader assetLoader = new AssetLoader(Paths.get(root));
        assetLoader.addMcClientSource();// Add minecraft client jar manually

        Context context = new Context(assetLoader, "output", root, true);

        BookParser bookParser = new BookParser();
        bookParser.processAllLanguages(context);
    }
}
