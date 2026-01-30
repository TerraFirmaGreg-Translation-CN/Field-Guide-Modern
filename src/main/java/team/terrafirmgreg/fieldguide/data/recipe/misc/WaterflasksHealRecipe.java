package team.terrafirmgreg.fieldguide.data.recipe.misc;

import team.terrafirmgreg.fieldguide.data.recipe.CraftingRecipe;
import team.terrafirmgreg.fieldguide.data.recipe.ShapedCraftingRecipe;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WaterflasksHealRecipe extends CraftingRecipe {
    private ShapedCraftingRecipe recipe;
}