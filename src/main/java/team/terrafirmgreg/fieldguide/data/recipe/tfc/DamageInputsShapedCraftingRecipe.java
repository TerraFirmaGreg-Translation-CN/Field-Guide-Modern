package team.terrafirmgreg.fieldguide.data.recipe.tfc;

import team.terrafirmgreg.fieldguide.data.recipe.CraftingRecipe;
import team.terrafirmgreg.fieldguide.data.recipe.ShapedCraftingRecipe;
import lombok.Data;

@Data
public class DamageInputsShapedCraftingRecipe extends CraftingRecipe {
    private ShapedCraftingRecipe recipe;
}