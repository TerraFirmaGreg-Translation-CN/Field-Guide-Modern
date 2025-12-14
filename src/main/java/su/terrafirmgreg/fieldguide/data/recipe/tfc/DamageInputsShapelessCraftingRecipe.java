package su.terrafirmgreg.fieldguide.data.recipe.tfc;

import su.terrafirmgreg.fieldguide.data.recipe.CraftingRecipe;
import su.terrafirmgreg.fieldguide.data.recipe.ShapelessCraftingRecipe;
import lombok.Data;

@Data
public class DamageInputsShapelessCraftingRecipe extends CraftingRecipe {
    private ShapelessCraftingRecipe recipe;
}