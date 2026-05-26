package team.terrafirmgreg.fieldguide.data.recipe.ingredient;

import team.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagIngredient implements Ingredient {
    private String tag;
}
