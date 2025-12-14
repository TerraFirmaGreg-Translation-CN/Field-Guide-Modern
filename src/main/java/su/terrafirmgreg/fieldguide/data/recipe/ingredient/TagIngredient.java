package su.terrafirmgreg.fieldguide.data.recipe.ingredient;

import su.terrafirmgreg.fieldguide.asset.ItemImageResult;
import su.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import su.terrafirmgreg.fieldguide.render.TextureRenderer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagIngredient implements Ingredient {
    private String tag;
    
    @Override
    public ItemImageResult toItemImage(TextureRenderer renderer) {
        return renderer.getItemImage("#" + tag, true);
    }
}