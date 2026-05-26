package team.terrafirmgreg.fieldguide.data.recipe.ingredient;

import team.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import lombok.Data;

import java.util.ArrayList;

@Data
public class ListIngredient extends ArrayList<Ingredient> implements Ingredient {
    private String type;
}
