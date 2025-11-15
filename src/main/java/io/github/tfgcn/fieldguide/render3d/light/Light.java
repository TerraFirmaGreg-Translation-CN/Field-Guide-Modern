package io.github.tfgcn.fieldguide.render3d.light;

import io.github.tfgcn.fieldguide.render3d.math.Vector4f;

/**
 * 光源
 * @author yanmaoyuan
 *
 */
public abstract class Light {

    // 光源的颜色
    protected Vector4f color;

    public Light() {
        color = new Vector4f(1, 1, 1, 1);
    }

    public Light(Vector4f color) {
        this.color = color;
    }

    public Vector4f getColor() {
        return color;
    }

    public void setColor(Vector4f color) {
        this.color = color;
    }
    
}
