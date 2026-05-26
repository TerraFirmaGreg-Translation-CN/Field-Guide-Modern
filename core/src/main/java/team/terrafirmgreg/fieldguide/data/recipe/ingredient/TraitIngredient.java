package team.terrafirmgreg.fieldguide.data.recipe.ingredient;

import team.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import lombok.Data;

@Data
public class TraitIngredient implements Ingredient {
    private String type;
    private String trait;
    private Ingredient ingredient;
}
