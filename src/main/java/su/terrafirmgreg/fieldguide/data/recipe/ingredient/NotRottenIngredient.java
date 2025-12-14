package su.terrafirmgreg.fieldguide.data.recipe.ingredient;

import su.terrafirmgreg.fieldguide.asset.ItemImageResult;
import su.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import su.terrafirmgreg.fieldguide.render.TextureRenderer;
import lombok.Data;

@Data
public class NotRottenIngredient implements Ingredient {
    private String type;

    private Ingredient ingredient;
    
    @Override
    public ItemImageResult toItemImage(TextureRenderer renderer) {
        return ingredient.toItemImage(renderer);
    }
}