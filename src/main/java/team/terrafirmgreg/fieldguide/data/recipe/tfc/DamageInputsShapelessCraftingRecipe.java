package team.terrafirmgreg.fieldguide.data.recipe.tfc;

import team.terrafirmgreg.fieldguide.data.recipe.CraftingRecipe;
import team.terrafirmgreg.fieldguide.data.recipe.ShapelessCraftingRecipe;
import lombok.Data;

@Data
public class DamageInputsShapelessCraftingRecipe extends CraftingRecipe {
    private ShapelessCraftingRecipe recipe;
}