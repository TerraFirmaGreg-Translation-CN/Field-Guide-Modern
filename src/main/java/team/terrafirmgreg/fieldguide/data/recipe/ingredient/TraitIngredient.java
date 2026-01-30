package team.terrafirmgreg.fieldguide.data.recipe.ingredient;

import team.terrafirmgreg.fieldguide.asset.ItemImageResult;
import team.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import team.terrafirmgreg.fieldguide.render.TextureRenderer;
import lombok.Data;

@Data
public class TraitIngredient implements Ingredient {
    private String type;
    private String trait;
    private Ingredient ingredient;
    
    @Override
    public ItemImageResult toItemImage(TextureRenderer renderer) {
        return ingredient.toItemImage(renderer);
    }
}