package su.terrafirmgreg.fieldguide.data.recipe.tfc;

import su.terrafirmgreg.fieldguide.data.recipe.CraftingRecipe;
import su.terrafirmgreg.fieldguide.data.recipe.ShapedCraftingRecipe;
import lombok.Data;

@Data
public class DamageInputsShapedCraftingRecipe extends CraftingRecipe {
    private ShapedCraftingRecipe recipe;
}