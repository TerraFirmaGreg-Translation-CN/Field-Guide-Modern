package team.terrafirmgreg.fieldguide.data.recipe;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ShapedCraftingRecipe extends CraftingRecipe {
    private List<String> pattern;
    private Map<String, Ingredient> key;
    @SerializedName("input_row")
    private Integer inputRow;// used for tfc
    @SerializedName("input_column")
    private Integer inputColumn;// used for tfc
}