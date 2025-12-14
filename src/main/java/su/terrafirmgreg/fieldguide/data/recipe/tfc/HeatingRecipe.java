package su.terrafirmgreg.fieldguide.data.recipe.tfc;

import com.google.gson.annotations.SerializedName;
import su.terrafirmgreg.fieldguide.data.recipe.BaseRecipe;
import su.terrafirmgreg.fieldguide.data.recipe.RecipeResult;
import su.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import lombok.Data;

@Data
public class HeatingRecipe extends BaseRecipe {
    private Ingredient ingredient;
    @SerializedName("result_item")
    private RecipeResult resultItem;
    private int temperature;
    private Float chance;
}