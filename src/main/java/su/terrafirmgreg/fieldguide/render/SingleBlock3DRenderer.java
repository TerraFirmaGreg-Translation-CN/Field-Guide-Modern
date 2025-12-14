package su.terrafirmgreg.fieldguide.render;

import su.terrafirmgreg.fieldguide.data.minecraft.blockmodel.BlockModel;
import su.terrafirmgreg.fieldguide.render3d.math.Vector3f;
import su.terrafirmgreg.fieldguide.render3d.scene.Node;

import java.awt.image.BufferedImage;

import static su.terrafirmgreg.fieldguide.render.BaseModelBuilder.SCALE;
import static su.terrafirmgreg.fieldguide.render.BaseModelBuilder.v3;

/**
 * 单个方块的3D渲染器
 */
public class SingleBlock3DRenderer extends BaseRenderer {

    public SingleBlock3DRenderer(BaseModelBuilder modelBuilder, int width, int height) {
        super(modelBuilder, width, height);
        
        // 设置平行投影摄像机
        camera.setParallel(-11 * SCALE, 11 * SCALE, -11 * SCALE, 11 * SCALE, -1000f, 1000f);
        camera.lookAt(v3(32f, 32f, 32f), v3(8, 8, 8), Vector3f.UNIT_Y);
    }

    /**
     * 渲染模型
     */
    public BufferedImage render(String modelId) {
        Node node = modelBuilder.buildModel(modelId);
        rootNode.attachChild(node);
        BufferedImage image = render();
        rootNode.detachChild(node);
        return image;
    }

    public BufferedImage render(BlockModel model) {
        Node node = modelBuilder.buildModel(model);
        rootNode.attachChild(node);
        BufferedImage image = render();
        rootNode.detachChild(node);
        return image;
    }

}