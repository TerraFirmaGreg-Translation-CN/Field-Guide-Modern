package io.github.tfgcn.fieldguide.render;

import io.github.tfgcn.fieldguide.asset.AssetLoader;
import io.github.tfgcn.fieldguide.data.minecraft.blockmodel.BlockModel;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class BlockStateModelBuilder extends BaseModelBuilder {

    public BlockStateModelBuilder(AssetLoader assetLoader) {
        super(assetLoader);
    }

    @Override
    protected BlockModel loadModel(String modelId) {
        if (modelId.startsWith("#")) {
            List<String> blocks = assetLoader.loadBlockTag(modelId.substring(1));
            modelId = blocks.get(0); // 获取第一个方块
        }
        BlockModel blockModel = assetLoader.loadBlockModelWithState(modelId);
        if (!blockModel.hasElements()) {
            // 返回空节点
            return new BlockModel(); // 需要根据你的BlockModel类调整
        }
        return blockModel;
    }
}