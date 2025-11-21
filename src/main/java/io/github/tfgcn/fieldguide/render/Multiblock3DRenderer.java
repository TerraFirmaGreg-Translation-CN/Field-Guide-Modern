package io.github.tfgcn.fieldguide.render;

import io.github.tfgcn.fieldguide.render3d.math.Vector3f;
import io.github.tfgcn.fieldguide.render3d.scene.Node;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.util.Map;

import static io.github.tfgcn.fieldguide.render.BaseModelBuilder.v3;

/**
 * 多方块结构的3D渲染器
 */
@Slf4j
public class Multiblock3DRenderer extends BaseRenderer {

    public Multiblock3DRenderer(BaseModelBuilder modelBuilder, int width, int height) {
        super(modelBuilder, width, height);
        
        // 设置透视投影摄像机
        camera.lookAt(v3(100, 100, 100), v3(0, 0, 0), Vector3f.UNIT_Y);
    }

    /**
     * 渲染多方块结构
     */
    public BufferedImage render(String[][] pattern, Map<String, String> mapping) {
        Node root = buildMultiblock(pattern, mapping);
        rootNode.attachChild(root);
        BufferedImage image = render();
        rootNode.detachChild(root);
        return image;
    }

    /**
     * 渲染场景
     */
    public BufferedImage render(Node scene) {
        rootNode.attachChild(scene);
        BufferedImage image = render();
        rootNode.detachChild(scene);
        return image;
    }

    /**
     * 构建多方块结构
     */
    public Node buildMultiblock(String[][] pattern, Map<String, String> mapping) {
        Node root = new Node();
        int height = pattern.length;
        int col = pattern[0].length;
        int row = pattern[0][0].length();
        log.debug("Model size: {}x{}x{}", col, height, row);

        // 调整摄像机位置
        int max = Math.max(Math.max(col, height), row);
        camera.lookAt(v3(max * 10, max * 10, max * 10), v3(0, 0, 0), Vector3f.UNIT_Y);

        float startX = -row * 8f;
        float startY = -height * 8f;
        float startZ = -col * 8f;

        for (int y = 0; y < height; y++) {
            String[] layer = pattern[height - y - 1];
            for (int z = 0; z < col; z++) {
                String line = layer[z];
                for (int x = 0; x < row; x++) {
                    char c = line.charAt(x);
                    if (c == ' ') {
                        continue;
                    }
                    String model = mapping.get(String.valueOf(c));
                    if (model == null || "AIR".equalsIgnoreCase(model) || "minecraft:air".equalsIgnoreCase(model)) {
                        continue;
                    }
                    Vector3f location = v3(x * 16 + startX, y * 16 + startY, z * 16 + startZ);
                    Node node = modelBuilder.buildModel(model);
                    node.getLocalTransform().setTranslation(location);
                    root.attachChild(node);
                }
            }
        }
        return root;
    }
}