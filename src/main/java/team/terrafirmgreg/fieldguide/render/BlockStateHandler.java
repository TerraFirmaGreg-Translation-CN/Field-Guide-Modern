package team.terrafirmgreg.fieldguide.render;

import team.terrafirmgreg.fieldguide.asset.AssetLoader;
import team.terrafirmgreg.fieldguide.data.minecraft.blockstate.*;
import team.terrafirmgreg.fieldguide.exception.AssetNotFoundException;
import team.terrafirmgreg.fieldguide.render3d.scene.Node;
import team.terrafirmgreg.fieldguide.render3d.math.Quaternion;
import team.terrafirmgreg.fieldguide.render3d.math.Vector3f;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * 处理BlockState到3D模型转换的核心类
 * 参考BlueMap的实现，支持variants和multipart两种格式
 */
@Slf4j
public class BlockStateHandler {

    private final AssetLoader assetLoader;
    private final BaseModelBuilder modelBuilder;

    public BlockStateHandler(AssetLoader assetLoader, BaseModelBuilder modelBuilder) {
        this.assetLoader = assetLoader;
        this.modelBuilder = modelBuilder;
    }

    /**
     * 根据方块ID构建3D模型
     * @param blockId 方块ID，格式如 "minecraft:stone" 或 "tfc:rock/raw/granite"
     * @return 3D模型节点
     */
    public Node buildBlockModel(String blockId) {
        try {
            // 加载BlockState JSON
            BlockState blockState = assetLoader.loadBlockState(blockId);

            if (blockState != null) {
                // 检查variants或multipart格式
                if (blockState.hasVariants()) {
                    return handleVariants(blockState.getVariants(), blockId);
                } else if (blockState.hasMultipart()) {
                    return handleMultipart(blockState.getMultipart(), blockId);
                }
            }

            // 如果没有BlockState，尝试直接加载模型
            return modelBuilder.buildModel(blockId);

        } catch (AssetNotFoundException e) {
            log.warn("BlockState not found for {}, trying direct model load", blockId);
            // 回退到直接模型加载
            return modelBuilder.buildModel(blockId);
        } catch (Exception e) {
            log.error("Failed to build model for " + blockId, e);
            // 返回一个错误标记模型
            return modelBuilder.buildModel("minecraft:block/missing");
        }
    }

    /**
     * 处理variants格式的BlockState
     * variants格式是简单的属性名到模型列表的映射
     */
    private Node handleVariants(LinkedHashMap<String, List<Variant>> variants, String blockId) {
        Node result = new Node();

        // 获取默认变体（没有属性或空字符串的变体）
        List<Variant> defaultVariants = variants.get("");

        if (defaultVariants == null || defaultVariants.isEmpty()) {
            // 如果没有默认变体，尝试使用第一个变体
            for (List<Variant> variantList : variants.values()) {
                if (variantList != null && !variantList.isEmpty()) {
                    defaultVariants = variantList;
                    break;
                }
            }
        }

        if (defaultVariants != null) {
            // 对于网页展示，我们通常只需要第一个变体
            Variant variant = defaultVariants.get(0);
            Node variantNode = buildVariantModel(variant, blockId);
            if (variantNode != null) {
                result.attachChild(variantNode);
            }
        }

        return result;
    }

    /**
     * 处理multipart格式的BlockState
     * multipart格式允许根据条件组合多个模型
     */
    private Node handleMultipart(List<MultiPartCase> multipartCases, String blockId) {
        Node result = new Node();

        for (MultiPartCase part : multipartCases) {
            // 检查条件是否匹配（对于展示，我们可以应用所有部分）
            // 真正的游戏中需要根据具体的BlockState属性来判断
            if (shouldApplyPart(part, blockId)) {
                for (Variant variant : part.getApply()) {
                    Node variantNode = buildVariantModel(variant, blockId);
                    if (variantNode != null) {
                        result.attachChild(variantNode);
                    }
                }
            }
        }

        return result;
    }

    /**
     * 判断是否应该应用multipart的某个部分
     * 对于网页展示，我们可以应用所有条件为null的部分
     */
    private boolean shouldApplyPart(MultiPartCase part, String blockId) {
        return part.getWhen() == null; // 条件为null表示总是应用
    }

    /**
     * 构建单个变体的模型
     */
    private Node buildVariantModel(Variant variant, String blockId) {
        String modelId = variant.getModel();

        // 处理模型引用
        if (modelId.startsWith("#")) {
            log.warn("Model reference not supported: {}", modelId);
            return null;
        }

        try {
            Node modelNode = modelBuilder.buildModel(modelId);

            // 应用变换
            if (variant.getX() != 0 || variant.getY() != 0 || variant.getZ() != 0) {
                applyRotation(modelNode, variant);
            }

            // 应用UV锁定
            if (variant.getUvlock() != null && variant.getUvlock()) {
                log.debug("UV lock not implemented for model: {}", modelId);
            }

            // 应用权重（对于随机选择，这里总是选择第一个）
            if (variant.getWeight() != 0 && variant.getWeight() > 1) {
                log.debug("Weight selection not implemented, using first variant for: {}", modelId);
            }

            return modelNode;

        } catch (Exception e) {
            log.error("Failed to load variant model: " + modelId, e);
            return null;
        }
    }

    /**
     * 应用旋转变换
     */
    private void applyRotation(Node node, Variant variant) {
        // 检查是否有旋转（x、y、z有默认值0）
        boolean hasRotation = false;

        // 创建总的旋转四元数
        Quaternion totalRotation = new Quaternion();

        // 累积旋转
        if (variant.getX() != 0) {
            float x = variant.getX() * 90; // Minecraft中x的值是0-3，对应0-270度
            float rad = (float) Math.toRadians(x);
            if (!hasRotation) {
                totalRotation.rotateX(rad);
                hasRotation = true;
            } else {
                Quaternion qx = new Quaternion().rotateX(rad);
                totalRotation.multLocal(qx);
            }
        }

        if (variant.getY() != 0) {
            float y = variant.getY() * 90; // Minecraft中y的值是0-3，对应0-270度
            float rad = (float) Math.toRadians(y);
            if (!hasRotation) {
                totalRotation.rotateY(rad);
                hasRotation = true;
            } else {
                Quaternion qy = new Quaternion().rotateY(rad);
                totalRotation.multLocal(qy);
            }
        }

        if (variant.getZ() != 0) {
            float z = variant.getZ() * 90;
            float rad = (float) Math.toRadians(z);
            if (!hasRotation) {
                totalRotation.rotateZ(rad);
                hasRotation = true;
            } else {
                Quaternion qz = new Quaternion().rotateZ(rad);
                totalRotation.multLocal(qz);
            }
        }

        // 应用旋转
        if (hasRotation) {
            node.getLocalTransform().setRotation(totalRotation);
        }
    }

    /**
     * 创建错误模型（用于无法加载的方块）
     */
    public Node createErrorModel() {
        try {
            return modelBuilder.buildModel("minecraft:block/missing");
        } catch (Exception e) {
            // 如果连错误模型都加载不了，创建一个简单的立方体
            return createSimpleCube();
        }
    }

    /**
     * 创建一个简单的立方体作为最后的回退
     */
    private Node createSimpleCube() {
        Node node = new Node();
        // TODO: 实现简单的立方体几何体
        // 这里需要设置基本的几何体和材质
        return node;
    }
}