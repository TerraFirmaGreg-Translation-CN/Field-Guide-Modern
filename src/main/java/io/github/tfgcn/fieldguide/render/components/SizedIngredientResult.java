package io.github.tfgcn.fieldguide.render.components;

import io.github.tfgcn.fieldguide.asset.ItemImageResult;

/**
 * 带数量的成分结果类
 */
public class SizedIngredientResult {
    public final ItemImageResult ingredient;
    public final int count;

    public SizedIngredientResult(ItemImageResult ingredient, int count) {
        this.ingredient = ingredient;
        this.count = count;
    }
}