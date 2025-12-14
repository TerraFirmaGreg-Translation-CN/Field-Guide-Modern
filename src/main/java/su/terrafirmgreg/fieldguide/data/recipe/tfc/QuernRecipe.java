package su.terrafirmgreg.fieldguide.data.recipe.tfc;

import su.terrafirmgreg.fieldguide.data.recipe.BaseRecipe;
import su.terrafirmgreg.fieldguide.data.recipe.RecipeResult;
import su.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import lombok.Data;

@Data
public class QuernRecipe extends BaseRecipe {
    private Ingredient ingredient;
    private RecipeResult result;
}