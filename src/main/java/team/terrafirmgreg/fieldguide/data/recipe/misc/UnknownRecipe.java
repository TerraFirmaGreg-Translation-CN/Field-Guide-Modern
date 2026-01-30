package team.terrafirmgreg.fieldguide.data.recipe.misc;

import team.terrafirmgreg.fieldguide.data.recipe.BaseRecipe;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class UnknownRecipe extends BaseRecipe {
    private Map<String, Object> data;
}