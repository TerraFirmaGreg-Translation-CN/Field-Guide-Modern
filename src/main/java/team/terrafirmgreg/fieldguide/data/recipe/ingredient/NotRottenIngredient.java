package team.terrafirmgreg.fieldguide.data.recipe.ingredient;

import team.terrafirmgreg.fieldguide.asset.ItemImageResult;
import team.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import team.terrafirmgreg.fieldguide.render.TextureRenderer;
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