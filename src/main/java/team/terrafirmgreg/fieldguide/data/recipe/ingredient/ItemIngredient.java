package team.terrafirmgreg.fieldguide.data.recipe.ingredient;

import team.terrafirmgreg.fieldguide.asset.ItemImageResult;
import team.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import team.terrafirmgreg.fieldguide.render.TextureRenderer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemIngredient implements Ingredient {
    private String item;
    private Integer count;

    @Override
    public ItemImageResult toItemImage(TextureRenderer renderer) {
        return renderer.getItemImage(item, true);
    }
}