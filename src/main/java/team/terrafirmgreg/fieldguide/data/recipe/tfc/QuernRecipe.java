package team.terrafirmgreg.fieldguide.data.recipe.tfc;

import team.terrafirmgreg.fieldguide.data.recipe.BaseRecipe;
import team.terrafirmgreg.fieldguide.data.recipe.RecipeResult;
import team.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import lombok.Data;

@Data
public class QuernRecipe extends BaseRecipe {
    private Ingredient ingredient;
    private RecipeResult result;
}