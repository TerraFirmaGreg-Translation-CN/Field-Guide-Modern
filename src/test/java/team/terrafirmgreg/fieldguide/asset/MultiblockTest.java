package team.terrafirmgreg.fieldguide.asset;

import team.terrafirmgreg.fieldguide.data.minecraft.blockmodel.BlockModel;
import team.terrafirmgreg.fieldguide.render.BaseModelBuilder;
import team.terrafirmgreg.fieldguide.render3d.Application;
import team.terrafirmgreg.fieldguide.render3d.math.Vector3f;
import team.terrafirmgreg.fieldguide.render3d.renderer.Camera;
import team.terrafirmgreg.fieldguide.render3d.scene.Node;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多方块结构测试类
 */
@Slf4j
public class MultiblockTest extends Application {

    private BaseModelBuilder modelBuilder;

    public static void main(String[] args) {
        MultiblockTest app = new MultiblockTest();
        app.start();
    }

    public MultiblockTest() {
        this.width = 512;
        this.height = 512;
        this.title = "Multiblock Test";
    }

    @Override
    protected void initialize() {
        String modpackPath = "Modpack-Modern";
        AssetLoader assetLoader = new AssetLoader(Paths.get(modpackPath));
        modelBuilder = new BaseModelBuilder(assetLoader) {
            @Override
            protected BlockModel loadModel(String modelId) {
                if (modelId.startsWith("#")) {
                    List<String> blocks = assetLoader.loadBlockTag(modelId.substring(1));
                    modelId = blocks.getFirst(); // 获取第一个方块
                }
                BlockModel blockModel = assetLoader.loadBlockModelWithState(modelId);
                if (!blockModel.hasElements()) {
                    // 返回空节点
                    return new BlockModel(); // 需要根据你的BlockModel类调整
                }
                return blockModel;
            }
        };

        // 定义多方块结构和材质映射
        String[][] pattern = new String[][] {
                {"  G  ", "  G  ", "GGCGG", "  G  ", "  G  "},
                {"XXXXX", "XXXXX", "XX0XX", "XXBXX", "XXXXX"}
        };
        Map<String, String> mapping = new HashMap<>();
        mapping.put("X", "tfc:rock/smooth/gabbro");
        mapping.put("G", "minecraft:light_blue_stained_glass");
        mapping.put("0", "tfc:charcoal_forge[heat_level=7]");
        mapping.put("C", "tfc:crucible");
        mapping.put("B", "tfc:bellows");

        // 构建多方块结构
        Node multiblockNode = buildMultiblock(pattern, mapping);
        rootNode.attachChild(multiblockNode);

        // 初始化摄像机
        Camera cam = getCamera();
        cam.lookAt(BaseModelBuilder.v3(100, 100, 100),
                BaseModelBuilder.v3(0, 0, 0), Vector3f.UNIT_Y);
    }

    @Override
    protected void update(float delta) {
    }

    private Node buildMultiblock(String[][] pattern, Map<String, String> mapping) {
        Node root = new Node();
        int height = pattern.length;
        int col = pattern[0].length;
        int row = pattern[0][0].length();

        System.out.printf("Building multiblock structure: %dx%dx%dx%n", col, height, row);

        float startX = -col * 8f;
        float startZ = -row * 8f;
        float startY = -height * 8f;

        for (int y = 0; y < height; y++) {
            String[] layer = pattern[height - y - 1];
            for (int z = 0; z < row; z++) {
                String line = layer[z];
                for (int x = 0; x < col; x++) {
                    char c = line.charAt(x);
                    if (c == ' ') {
                        continue;
                    }
                    String modelId = mapping.get(String.valueOf(c));
                    if (modelId == null || "AIR".equalsIgnoreCase(modelId) || "minecraft:air".equalsIgnoreCase(modelId)) {
                        continue;
                    }

                    // 使用 BaseModelBuilder 构建单个方块模型
                    Node blockNode = modelBuilder.buildModel(modelId);
                    if (blockNode != null) {
                        // 设置方块位置
                        Vector3f location = BaseModelBuilder.v3(x * 16 + startX, y * 16 + startY, z * 16 + startZ);
                        blockNode.getLocalTransform().setTranslation(location);
                        root.attachChild(blockNode);
                    }
                }
            }
        }

        return root;
    }
}