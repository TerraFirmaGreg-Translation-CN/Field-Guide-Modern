package team.terrafirmgreg.fieldguide.data.recipe.tfc;

import team.terrafirmgreg.fieldguide.data.recipe.BaseRecipe;
import team.terrafirmgreg.fieldguide.data.recipe.RecipeResult;
import team.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import lombok.Data;

import java.util.List;

@Data
public class GlassworkingRecipe extends BaseRecipe {
    private List<String> operations;
    private Ingredient batch;
    private RecipeResult result;
}