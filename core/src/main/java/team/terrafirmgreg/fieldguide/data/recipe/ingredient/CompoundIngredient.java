package team.terrafirmgreg.fieldguide.data.recipe.ingredient;

import team.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CompoundIngredient implements Ingredient {
    private String type;
    private List<Ingredient> children;
    private Ingredient ingredient;
    private Map<String, Object> properties;
}
