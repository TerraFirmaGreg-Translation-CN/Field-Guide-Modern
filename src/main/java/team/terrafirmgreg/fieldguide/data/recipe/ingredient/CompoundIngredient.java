package team.terrafirmgreg.fieldguide.data.recipe.ingredient;

import team.terrafirmgreg.fieldguide.asset.ItemImageResult;
import team.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import team.terrafirmgreg.fieldguide.render.TextureRenderer;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CompoundIngredient implements Ingredient {
    private String type;
    private List<Ingredient> children;
    private Ingredient ingredient;
    private Map<String, Object> properties;
    
    @Override
    public ItemImageResult toItemImage(TextureRenderer renderer) {
        switch (type) {
            case "tfc:and":
                // 处理 AND 逻辑
                StringBuilder items = new StringBuilder();
                for (Ingredient child : children) {
                    if (child instanceof ItemIngredient) {
                        if (!items.isEmpty()) {
                            items.append(",");
                        }
                        items.append(((ItemIngredient) child).getItem());
                    }
                }
                return renderer.getItemImage(items.toString(), true);
                
            case "tfc:has_trait":
            case "tfc:lacks_trait":
            case "tfc:not_rotten":
                return ingredient.toItemImage(renderer);
                
            default:
                throw new UnsupportedOperationException("Unknown compound ingredient type: " + type);
        }
    }
}