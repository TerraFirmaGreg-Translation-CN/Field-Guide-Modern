package io.github.tfgcn.fieldguide.render3d.light;

import io.github.tfgcn.fieldguide.render3d.math.Vector4f;

/**
 * 环境光
 * @author yanmaoyuan
 *
 */
public class AmbientLight extends Light {

    public AmbientLight() {
        super();
    }

    public AmbientLight(Vector4f color) {
        super(color);
    }
    
}
