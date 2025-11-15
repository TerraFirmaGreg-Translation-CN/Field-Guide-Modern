package io.github.tfgcn.fieldguide.minecraft;

import lombok.Data;

import java.util.*;

/**
 * @see <a href="https://zh.minecraft.wiki/w/%E6%A8%A1%E5%9E%8B">模型</a>
 * @see <a href="https://zh.minecraft.wiki/w/Tutorial:%E5%88%B6%E4%BD%9C%E8%B5%84%E6%BA%90%E5%8C%85/%E6%A8%A1%E5%9E%8B">Tutorial:制作资源包/模型</a>
 */
@Data
public class BlockModel {

    private String credit;// Blockbench
    private String parent;
    private Boolean ambientOcclusion;
    private Map<String, String> textures;
    private List<ModelElement> elements;
    private Map<String, DisplayTransform> display;
    private String guiLight;// side, face
    private List<ModelOverride> overrides;

    private String loader;

    private transient BlockModel parentModel;
    private transient Set<String> inherits = new TreeSet<>();

    public void mergeWithParent() {
        if (this.parentModel == null) {
            initRootModel();
            return;
        }

        // ambientOcclusion
        if (this.ambientOcclusion == null) {
            this.ambientOcclusion = this.parentModel.ambientOcclusion;
        }

        // textures
        if (this.textures == null) {
            if (parentModel.textures != null) {
                this.textures = new HashMap<>(this.parentModel.textures);
            }
        } else if (this.parentModel.textures != null) {
            Map<String, String> mergedTextures = new HashMap<>(this.parentModel.textures);
            mergedTextures.putAll(this.textures);
            this.textures = mergedTextures;
        }

        // elements
        if (this.elements == null) {
            this.elements = this.parentModel.elements != null ? new ArrayList<>(this.parentModel.elements) : null;
        }

        // display
        if (this.display == null) {
            if (parentModel.display != null) {
                this.display = new HashMap<>(this.parentModel.display);
            }
        } else if (this.parentModel.display != null) {
            Map<String, DisplayTransform> mergedDisplay = new HashMap<>(this.parentModel.display);
            mergedDisplay.putAll(this.display);
            this.display = mergedDisplay;
        }

        // guiLight
        if (this.guiLight == null) {
            this.guiLight = this.parentModel.guiLight;
        }

        // overrides
        if (this.overrides == null) {
            if (this.parentModel.getOverrides() != null) {
                this.overrides = new ArrayList<>(this.parentModel.overrides);
            }
        }

        // loader
        if (this.loader == null) {
            this.loader = this.parentModel.loader;
        }

        this.inherits.addAll(parentModel.inherits);
    }

    private void initRootModel() {
        if (ambientOcclusion == null) {
            ambientOcclusion = true;
        }

        if (guiLight == null) {
            guiLight = "side";
        }
    }

    public boolean instanceOf(String modelId) {
        return inherits.contains(modelId);
    }

    @Override
    public String toString() {
        return "BlockModel{" +
                "parent='" + parent + '\'' +
                ", ambientOcclusion=" + ambientOcclusion +
                ", textures=" + textures +
                ", elements=" + elements +
                ", display=" + display +
                ", guiLight='" + guiLight + '\'' +
                ", overrides=" + overrides +
                ", loader='" + loader + '\'' +
                '}';
    }
}
