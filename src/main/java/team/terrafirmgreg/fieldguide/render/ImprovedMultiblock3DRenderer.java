package team.terrafirmgreg.fieldguide.render;

import team.terrafirmgreg.fieldguide.render3d.math.Vector3f;
import team.terrafirmgreg.fieldguide.render3d.scene.Node;
import team.terrafirmgreg.fieldguide.asset.AssetLoader;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.util.Map;

import static team.terrafirmgreg.fieldguide.render.BaseModelBuilder.v3;

/**
 * 改进的多方块结构3D渲染器
 * 使用BlockStateHandler正确处理BlockState到3D模型的转换
 */
@Slf4j
public class ImprovedMultiblock3DRenderer extends BaseRenderer {

    private final BlockStateHandler blockStateHandler;

    public ImprovedMultiblock3DRenderer(AssetLoader assetLoader, int width, int height) {
        super(new BaseModelBuilder(assetLoader), width, height);
        this.blockStateHandler = new BlockStateHandler(assetLoader, modelBuilder);

        // 设置透视投影摄像机
        camera.lookAt(v3(100, 100, 100), v3(0, 0, 0), Vector3f.UNIT_Y);
    }

    /**
     * 改进的渲染方法，支持更多的方块类型
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

        // 调整摄像机位置 - 优化视角
        int maxSize = Math.max(Math.max(col, height), row);
        float cameraDistance = maxSize * 0.8f;
        float cameraHeight = maxSize * 0.6f;
        camera.lookAt(
            v3(cameraDistance, cameraHeight, cameraDistance),
            v3(row * 8, height * 8, col * 8),
            Vector3f.UNIT_Y
        );

        // 计算起始位置，使模型居中
        float startX = -row * 8f;
        float startY = -height * 8f;
        float startZ = -col * 8f;

        // 构建每个方块
        for (int y = 0; y < height; y++) {
            String[] layer = pattern[height - y - 1]; // 从下往上构建
            for (int z = 0; z < col; z++) {
                String line = layer[z];
                for (int x = 0; x < row; x++) {
                    char c = line.charAt(x);
                    if (c == ' ') {
                        continue;
                    }

                    String blockId = mapping.get(String.valueOf(c));
                    if (blockId == null || "AIR".equalsIgnoreCase(blockId) || "minecraft:air".equalsIgnoreCase(blockId)) {
                        continue;
                    }

                    // 使用BlockStateHandler构建模型
                    Node blockNode = buildBlock(blockId, x, y, z, startX, startY, startZ);
                    if (blockNode != null) {
                        root.attachChild(blockNode);
                    }
                }
            }
        }

        return root;
    }

    /**
     * 构建单个方块
     */
    private Node buildBlock(String blockId, int x, int y, int z, float startX, float startY, float startZ) {
        try {
            // 使用BlockStateHandler处理方块
            Node blockNode = blockStateHandler.buildBlockModel(blockId);

            if (blockNode != null) {
                // 设置方块位置（使用方块中心点）
                Vector3f location = v3(
                    x * 16 + 8 + startX,
                    y * 16 + 8 + startY,
                    z * 16 + 8 + startZ
                );
                blockNode.getLocalTransform().setTranslation(location);

                // 添加调试信息
                log.debug("Added block {} at ({}, {}, {})", blockId, x, y, z);
            }

            return blockNode;

        } catch (Exception e) {
            log.error("Failed to build block: " + blockId, e);
            // 返回一个错误方块
            Node errorNode = blockStateHandler.createErrorModel();
            if (errorNode != null) {
                Vector3f location = v3(
                    x * 16 + 8 + startX,
                    y * 16 + 8 + startY,
                    z * 16 + 8 + startZ
                );
                errorNode.getLocalTransform().setTranslation(location);
            }
            return errorNode;
        }
    }

    /**
     * 获取BlockStateHandler，用于外部配置
     */
    public BlockStateHandler getBlockStateHandler() {
        return blockStateHandler;
    }
}