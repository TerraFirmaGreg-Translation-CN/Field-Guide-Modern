package team.terrafirmgreg.fieldguide.data.recipe.ingredient;

import team.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import lombok.Data;

@Data
public class NotRottenIngredient implements Ingredient {
    private String type;
    private Ingredient ingredient;
}
