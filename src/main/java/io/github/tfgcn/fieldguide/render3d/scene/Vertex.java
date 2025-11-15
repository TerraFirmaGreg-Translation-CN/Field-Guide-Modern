package io.github.tfgcn.fieldguide.render3d.scene;

import io.github.tfgcn.fieldguide.render3d.math.Vector2f;
import io.github.tfgcn.fieldguide.render3d.math.Vector3f;
import io.github.tfgcn.fieldguide.render3d.math.Vector4f;

/**
 * 顶点数据
 * @author yanmaoyuan
 *
 */
public class Vertex {

    public Vector3f position;  // 顶点位置
    public Vector3f normal;    // 顶点法线
    public Vector4f color;     // 顶点颜色
    public Vector2f texCoord;  // 纹理坐标

}
