package su.terrafirmgreg.fieldguide.data.recipe.ingredient;

import su.terrafirmgreg.fieldguide.asset.ItemImageResult;
import su.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import su.terrafirmgreg.fieldguide.render.TextureRenderer;
import lombok.Data;

import java.util.ArrayList;

@Data
public class ListIngredient extends ArrayList<Ingredient> implements Ingredient {
    private String type;

    @Override
    public ItemImageResult toItemImage(TextureRenderer renderer) {
        StringBuilder items = new StringBuilder();
        for (Ingredient child : this) {
            if (child instanceof ItemIngredient) {
                if (!items.isEmpty()) {
                    items.append(",");
                }
                items.append(((ItemIngredient) child).getItem());
            }
        }
        return renderer.getItemImage(items.toString(), true);
    }
}