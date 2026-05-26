package team.terrafirmgreg.fieldguide.data.recipe.tfc;

import com.google.gson.annotations.SerializedName;
import team.terrafirmgreg.fieldguide.data.recipe.BaseRecipe;
import team.terrafirmgreg.fieldguide.data.recipe.RecipeResult;
import team.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import lombok.Data;

@Data
public abstract class BarrelRecipe extends BaseRecipe {
    @SerializedName("input_item")
    protected BarrelInputItem inputItem;
    @SerializedName("input_fluid")
    protected FluidIngredient inputFluid;
    @SerializedName("output_item")
    protected RecipeResult outputItem;
    @SerializedName("output_fluid")
    protected FluidOutput outputFluid;
    protected Integer duration;

    @Data
    public static class BarrelInputItem {
        private Ingredient ingredient;
        private Integer count;
    }

    @Data
    public static class FluidIngredient {
        private Ingredient ingredient;
        private Integer amount;
    }

    @Data
    public static class FluidOutput {
        private String fluid;
        private Integer amount;
    }
}