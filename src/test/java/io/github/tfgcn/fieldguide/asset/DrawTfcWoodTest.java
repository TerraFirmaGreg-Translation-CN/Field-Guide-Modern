package io.github.tfgcn.fieldguide.asset;

import io.github.tfgcn.fieldguide.data.tfc.TFCWood;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Paths;

import static io.github.tfgcn.fieldguide.render.TextureRenderer.multiplyImageByColor;

/**
 * desc:
 *
 * @author yanmaoyuan
 */
public class DrawTfcWoodTest {
    public static void main(String[] args) {
        String modpackPath = "Modpack-Modern";
        AssetLoader assetLoader = new AssetLoader(Paths.get(modpackPath));

        BufferedImage lumberBase = assetLoader.loadTexture("tfc:item/wood/lumber");
        BufferedImage twigBase = assetLoader.loadTexture("tfc:item/wood/twig");
        for (TFCWood wood : TFCWood.values()) {

            String name = wood.getSerializedName();
            Color woodColor = new Color(wood.getWoodColor().getCol());

            BufferedImage lumber = multiplyImageByColor(lumberBase, woodColor);
            BufferedImage twig = multiplyImageByColor(twigBase, woodColor);

            try {
                ImageIO.write(lumber, "png", new File("output/lumber_" + name + ".png"));
                ImageIO.write(twig, "png", new File("output/twig_" + name + ".png"));
            } catch (Exception ignored) {
            }
        }
    }
}
