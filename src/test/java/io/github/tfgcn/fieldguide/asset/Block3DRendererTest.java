package io.github.tfgcn.fieldguide.asset;

import io.github.tfgcn.fieldguide.render.Block3DRenderer;

import java.awt.image.BufferedImage;
import java.nio.file.Paths;

/**
 * desc:
 *
 * @author yanmaoyuan
 */
public class Block3DRendererTest {

    public static void main(String[] args) {
        String modpackPath = "Modpack-Modern";
        AssetLoader assetLoader = new AssetLoader(Paths.get(modpackPath));
        Block3DRenderer renderer = new Block3DRenderer(assetLoader, 256, 256);
        BufferedImage image = renderer.render("beneath:block/unposter");
    }
}
