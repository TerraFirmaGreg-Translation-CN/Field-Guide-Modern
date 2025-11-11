package io.github.tfgcn.fieldguide.mc;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @see <a href="https://zh.minecraft.wiki/w/%E6%A8%A1%E5%9E%8B">模型</a>
 * @see <a href="https://zh.minecraft.wiki/w/Tutorial:%E5%88%B6%E4%BD%9C%E8%B5%84%E6%BA%90%E5%8C%85/%E6%A8%A1%E5%9E%8B">Tutorial:制作资源包/模型</a>
 */
@Data
public class BlockModel {

    private String parent;
    private Boolean ambientOcclusion = true;
    private Map<String, String> textures;
    private List<ModelElement> elements;
    private Map<String, DisplayTransform> display;
    private String guiLight = "side";// side, face
    private List<ModelOverride> overrides;

    private String loader;

    private transient BlockModel parentModel;
}
