package su.terrafirmgreg.fieldguide.data.recipe.tfc;

import com.google.gson.annotations.SerializedName;
import su.terrafirmgreg.fieldguide.data.recipe.BaseRecipe;
import su.terrafirmgreg.fieldguide.data.recipe.RecipeResult;
import su.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import lombok.Data;

import java.util.List;

@Data
public class AnvilRecipe extends BaseRecipe {
    private Ingredient input;
    private RecipeResult result;
    private int tier;
    private List<String> rules;
    @SerializedName("apply_forging_bonus")
    private Boolean applyForgingBonus;
}