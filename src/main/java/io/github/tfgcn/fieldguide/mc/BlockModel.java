package io.github.tfgcn.fieldguide.mc;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @see <a href="https://zh.minecraft.wiki/w/%E6%A8%A1%E5%9E%8B?oldid=495044&variant=zh-cn">模型</a>
 */
@Data
public class BlockModel {

    private String parent;
    private Boolean ambientOcclusion;
    private Map<String, String> textures;
    private List<ModelElement> elements;
    private Map<String, DisplayTransform> display;
    private String guiLight;
    private List<ModelOverride> overrides;

    private String loader;

    private transient BlockModel parentModel;
}
