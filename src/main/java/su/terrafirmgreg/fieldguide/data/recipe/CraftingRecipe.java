package su.terrafirmgreg.fieldguide.data.recipe;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public abstract class CraftingRecipe extends BaseRecipe {
    protected String group;
    protected String category;
    @SerializedName("show_notification")
    protected Boolean showNotification;
    protected RecipeResult result;
}