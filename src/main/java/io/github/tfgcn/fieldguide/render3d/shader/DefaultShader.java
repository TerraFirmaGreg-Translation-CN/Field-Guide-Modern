package io.github.tfgcn.fieldguide.render3d.shader;

import io.github.tfgcn.fieldguide.render3d.material.Texture;
import io.github.tfgcn.fieldguide.render3d.math.Vector4f;
import io.github.tfgcn.fieldguide.render3d.scene.RasterizationVertex;
import io.github.tfgcn.fieldguide.render3d.scene.Vertex;

/**
 * 默认着色器
 * @author yanmaoyuan
 *
 */
public class DefaultShader extends Shader {

    @Override
    public RasterizationVertex vertexShader(Vertex vertex) {
        RasterizationVertex out = copy(vertex);

        // 模型-观察-透视 变换
        worldViewProjectionMatrix.mult(out.position, out.position);
        
        return out;
    }

    @Override
    public boolean fragmentShader(RasterizationVertex frag) {
        Texture texture = material.getDiffuseMap();
        if (texture != null) {
            Vector4f texColor = texture.sample2d(frag.texCoord);
            frag.color.multLocal(texColor);
        }
        
        return true;
    }

}
