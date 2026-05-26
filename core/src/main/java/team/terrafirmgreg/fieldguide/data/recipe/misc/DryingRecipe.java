package team.terrafirmgreg.fieldguide.data.recipe.misc;

import team.terrafirmgreg.fieldguide.data.recipe.BaseRecipe;
import team.terrafirmgreg.fieldguide.data.recipe.RecipeResult;
import team.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import lombok.Data;

@Data
public class DryingRecipe extends BaseRecipe {
    private Ingredient ingredient;
    private RecipeResult result;
}