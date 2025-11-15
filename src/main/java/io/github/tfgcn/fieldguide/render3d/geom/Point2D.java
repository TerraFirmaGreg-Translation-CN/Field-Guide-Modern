package io.github.tfgcn.fieldguide.render3d.geom;
import io.github.tfgcn.fieldguide.render3d.math.ColorRGBA;
import io.github.tfgcn.fieldguide.render3d.renderer.ImageRaster;

/**
 * 代表一个2D点。
 * @author yanmaoyuan
 *
 */
public class Point2D implements Drawable{

    public int x, y;
    public ColorRGBA color;
    
    public void draw(ImageRaster raster) {
        raster.drawPixel(x, y, color);
    }
}
