package su.terrafirmgreg.fieldguide.data.recipe.misc;

import su.terrafirmgreg.fieldguide.data.recipe.CraftingRecipe;
import su.terrafirmgreg.fieldguide.data.recipe.ShapedCraftingRecipe;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WaterflasksHealRecipe extends CraftingRecipe {
    private ShapedCraftingRecipe recipe;
}