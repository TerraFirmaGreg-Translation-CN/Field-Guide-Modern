package team.terrafirmgreg.fieldguide.data.recipe.ingredient;

import team.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemIngredient implements Ingredient {
    private String item;
    private Integer count;
}
