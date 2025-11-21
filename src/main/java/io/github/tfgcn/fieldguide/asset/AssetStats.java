package io.github.tfgcn.fieldguide.asset;

import lombok.Data;

import java.util.Set;
import java.util.TreeSet;

/**
 * desc:
 *
 * @author yanmaoyuan
 */
@Data
public class AssetStats {
    private Set<String> missingAssets = new TreeSet<>();
    private Set<String> missingRecipes = new TreeSet<>();
    private Set<String> missingItems = new TreeSet<>();
    private Set<String> missingBlocks = new TreeSet<>();
    private Set<String> missingFluids = new TreeSet<>();
    private Set<String> missingEntities = new TreeSet<>();
    private Set<String> missingLang = new TreeSet<>();
    private Set<String> missingModels = new TreeSet<>();
    private Set<String> missingTextures = new TreeSet<>();

    public void addMissingAsset(String asset) {
        missingAssets.add(asset);
    }

    public void addMissingRecipe(String recipe) {
        missingRecipes.add(recipe);
    }

    public void addMissingItem(String item) {
        missingItems.add(item);
    }

    public void addMissingBlock(String block) {
        missingBlocks.add(block);
    }

    public void addMissingFluid(String fluid) {
        missingFluids.add(fluid);
    }

    public void addMissingEntity(String entity) {
        missingEntities.add(entity);
    }

    public void addMissingLang(String lang) {
        missingLang.add(lang);
    }

    public void addMissingModel(String model) {
        missingModels.add(model);
    }

    public void addMissingTexture(String texture) {
        missingTextures.add(texture);
    }

    public void print() {
        System.out.println("Missing assets: " + missingAssets.size());
        for (String asset : missingAssets) {
            System.out.println(asset);
        }
        System.out.println("Missing recipes: " + missingRecipes.size());
        for (String recipe : missingRecipes) {
            System.out.println(recipe);
        }

        System.out.println("Missing items: " + missingItems.size());
        for (String item : missingItems) {
            System.out.println(item);
        }

        System.out.println("Missing blocks: " + missingBlocks.size());
        for (String block : missingBlocks) {
            System.out.println(block);
        }

        System.out.println("Missing fluids: " + missingFluids.size());
        for (String fluid : missingFluids) {
            System.out.println(fluid);
        }

        System.out.println("Missing entities: " + missingEntities.size());
        for (String entity : missingEntities) {
            System.out.println(entity);
        }

        System.out.println("Missing lang: " + missingLang.size());
        for (String lang : missingLang) {
            System.out.println(lang);
        }

        System.out.println("Missing models: " + missingModels.size());
        for (String model : missingModels) {
            System.out.println(model);
        }

        System.out.println("Missing textures: " + missingTextures.size());
        for (String texture : missingTextures) {
            System.out.println(texture);
        }
    }

}
