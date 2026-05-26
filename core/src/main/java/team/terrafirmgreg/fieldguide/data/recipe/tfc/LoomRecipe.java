package team.terrafirmgreg.fieldguide.data.recipe.tfc;

import com.google.gson.annotations.SerializedName;
import team.terrafirmgreg.fieldguide.data.recipe.BaseRecipe;
import team.terrafirmgreg.fieldguide.data.recipe.RecipeResult;
import team.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import lombok.Data;

@Data
public class LoomRecipe extends BaseRecipe {
    private LoomIngredient ingredient;
    private RecipeResult result;
    @SerializedName("steps_required")
    private int stepsRequired;
    @SerializedName("in_progress_texture")
    private String inProgressTexture;

    @Data
    public static class LoomIngredient {
        private Ingredient ingredient;
        private Integer count;
    }
}