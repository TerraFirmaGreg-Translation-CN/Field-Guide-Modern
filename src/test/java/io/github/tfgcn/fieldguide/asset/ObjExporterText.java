package io.github.tfgcn.fieldguide.asset;

import io.github.tfgcn.fieldguide.MCMeta;
import io.github.tfgcn.fieldguide.Versions;
import io.github.tfgcn.fieldguide.mc.BlockModel;
import io.github.tfgcn.fieldguide.opengl.OBJExporter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

/**
 * desc:
 *
 * @author yanmaoyuan
 */
public class ObjExporterText {

    public static void main(String[] args) throws IOException {
        // The TerraFirmaGreg modpack directory
        //String modpackPath = "/Users/yanmaoyuan/HMCL/.minecraft/versions/TerraFirmaGreg-Modern-0.11.7";
        String modpackPath = "E:\\HMCL-3.6.12\\.minecraft\\versions\\TerraFirmaGreg-Modern-0.11.7";

        modpackPath = modpackPath.replace("\\", "/");

        MCMeta.loadCache(Versions.MC_VERSION, Versions.FORGE_VERSION, Versions.LANGUAGES);

        AssetLoader assetLoader = new AssetLoader(Paths.get(modpackPath));

        BlockModel itemModel = assetLoader.loadItemModel("tfc:metal/anvil/bismuth_bronze");

        //BlockModel blockModel = assetLoader.loadModel("tfc:block/metal/anvil/bismuth_bronze");
        BlockModel blockModel = assetLoader.loadModel("tfc:block/barrel_side_sealed");

        OBJExporter exporter = new OBJExporter(assetLoader);
        exporter.exportToOBJ(blockModel, "output/1.obj");
        System.out.println(itemModel);
    }
}
