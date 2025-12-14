package su.terrafirmgreg.fieldguide.data.recipe.misc;

import su.terrafirmgreg.fieldguide.data.recipe.BaseRecipe;
import su.terrafirmgreg.fieldguide.data.recipe.RecipeResult;
import su.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import lombok.Data;

@Data
public class DryingRecipe extends BaseRecipe {
    private Ingredient ingredient;
    private RecipeResult result;
}