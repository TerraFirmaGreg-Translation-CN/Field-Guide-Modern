package team.terrafirmgreg.fieldguide.data.recipe.ingredient;

import com.google.gson.annotations.SerializedName;
import team.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import lombok.Data;

@Data
public class FluidItemIngredient implements Ingredient {
    private String type;
    @SerializedName("fluid_ingredient")
    private FluidIngredient fluidIngredient;
}
