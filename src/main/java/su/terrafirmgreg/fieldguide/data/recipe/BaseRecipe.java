package su.terrafirmgreg.fieldguide.data.recipe;

import com.google.gson.JsonObject;
import lombok.Data;

@Data
public abstract class BaseRecipe implements Recipe {
    protected String type;
    protected String id;
    protected JsonObject json;
    
    @Override
    public String getType() {
        return type;
    }
    
    @Override
    public String getId() {
        return id;
    }
}