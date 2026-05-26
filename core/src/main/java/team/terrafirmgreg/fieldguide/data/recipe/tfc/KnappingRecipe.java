package team.terrafirmgreg.fieldguide.data.recipe.tfc;

import com.google.gson.annotations.SerializedName;
import team.terrafirmgreg.fieldguide.data.recipe.BaseRecipe;
import team.terrafirmgreg.fieldguide.data.recipe.RecipeResult;
import team.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import lombok.Data;

import java.util.List;

@Data
public class KnappingRecipe extends BaseRecipe {
    @SerializedName("knapping_type")
    private String knappingType;
    private List<String> pattern;
    @SerializedName("outside_slot_required")
    private Boolean outsideSlotRequired;
    private Ingredient ingredient; // 用于岩石敲击
    private RecipeResult result;
}
