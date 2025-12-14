package su.terrafirmgreg.fieldguide.render3d.geom;

import su.terrafirmgreg.fieldguide.render3d.renderer.ImageRaster;

/**
 * 代表一个可渲染物体。
 * 
 * @author yanmaoyuan
 *
 */
public interface Drawable {

    public void draw(ImageRaster imageRaster);
    
}
