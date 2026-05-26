package team.terrafirmgreg.fieldguide.export.resources;

import com.google.gson.JsonObject;
import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.recipe.RecipesEventJS;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Snapshot of KubeJS {@link RecipeJS#json} captured during {@link RecipesEventJS#post}
 * (before {@code RecipesEventJS.instance} is cleared). Export runs much later on the integrated server.
 */
public final class KubeJsRecipeJsonCache {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");

    private static final Map<String, JsonObject> BY_ID = new LinkedHashMap<>();

    private KubeJsRecipeJsonCache() {}

    public static void capture(RecipesEventJS event) {
        BY_ID.clear();
        if (event == null) {
            return;
        }
        for (RecipeJS recipe : event.takenIds.values()) {
            store(recipe);
        }
        for (RecipeJS recipe : event.addedRecipes) {
            store(recipe);
        }
        LOGGER.info("[recipes] KubeJS json cache: {} recipe ids", BY_ID.size());
    }

    public static JsonObject get(ResourceLocation recipeId) {
        return BY_ID.get(recipeId.toString());
    }

    public static int size() {
        return BY_ID.size();
    }

    private static boolean store(RecipeJS recipe) {
        if (recipe == null || recipe.removed) {
            return false;
        }
        try {
            if (recipe.hasChanged()) {
                recipe.serialize();
            }
            JsonObject json = recipe.json;
            if (json == null || json.entrySet().isEmpty()) {
                return false;
            }
            ResourceLocation id = recipe.id != null ? recipe.id : recipe.getOrCreateId();
            JsonObject copy = json.deepCopy();
            if (!copy.has("type")) {
                copy.addProperty("type", recipe.getSerializationTypeFunction().idString);
            }
            BY_ID.put(id.toString(), copy);
            return true;
        } catch (Exception e) {
            LOGGER.trace("[recipes] KubeJS cache skip {}: {}", recipe, e.getMessage());
            return false;
        }
    }
}
