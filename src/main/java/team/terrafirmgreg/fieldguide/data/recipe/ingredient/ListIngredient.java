package team.terrafirmgreg.fieldguide.data.recipe.ingredient;

import team.terrafirmgreg.fieldguide.asset.ItemImageResult;
import team.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import team.terrafirmgreg.fieldguide.render.TextureRenderer;
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