package su.terrafirmgreg.fieldguide.data.recipe;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class ShapelessCraftingRecipe extends CraftingRecipe {
    private List<Ingredient> ingredients;

    @SerializedName("primary_ingredient")
    private Ingredient primaryIngredient;// used for firmalife food recipe
}