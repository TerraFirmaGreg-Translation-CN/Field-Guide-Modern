package team.terrafirmgreg.fieldguide.asset;

import team.terrafirmgreg.fieldguide.render.BaseModelBuilder;
import team.terrafirmgreg.fieldguide.render.SingleBlock3DRenderer;

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
        SingleBlock3DRenderer renderer = new SingleBlock3DRenderer(new BaseModelBuilder(assetLoader), 256, 256);
        BufferedImage image = renderer.render("beneath:block/unposter");
    }
}
