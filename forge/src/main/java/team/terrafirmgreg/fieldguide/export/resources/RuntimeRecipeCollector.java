package team.terrafirmgreg.fieldguide.export.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Collects live recipes from the integrated server's {@link RecipeManager}.
 *
 * <p>KubeJS 1.20.1 rebuilds the manager during {@code ServerEvents.RECIPES}; script recipes are
 * not in datapack {@code recipes/} JSON. Prefer Forge {@link RecipeManager#getRecipes()}, then merge
 * any missing ids from {@link RecipeManager#getAllRecipesFor(RecipeType)} (same pool EMI uses per type).</p>
 */
final class RuntimeRecipeCollector {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");

    private static final String[] WATCH_NAMESPACES = {"tfg", "gtceu", "kubejs"};

    private RuntimeRecipeCollector() {}

    record CollectResult(Map<String, Map<String, JsonElement>> byNamespace, int collected, int failures) {}

    static CollectResult collect(MinecraftServer server) {
        RecipeManager recipeManager = server.getRecipeManager();
        Map<ResourceLocation, Recipe<?>> recipes = new LinkedHashMap<>();

        int getRecipesListed = 0;
        try {
            Collection<Recipe<?>> all = recipeManager.getRecipes();
            for (Recipe<?> recipe : all) {
                if (recipe == null) {
                    continue;
                }
                ResourceLocation id = recipe.getId();
                if (id == null || ResourceExportFilter.isExcluded(id)) {
                    continue;
                }
                getRecipesListed++;
                recipes.putIfAbsent(id, recipe);
            }
        } catch (Exception e) {
            LOGGER.error("[recipes] RecipeManager.getRecipes() failed: {}", e.toString());
        }

        int uniqueFromGetRecipes = recipes.size();
        int addedByType = mergeFromAllRecipeTypes(recipeManager, recipes);

        if (recipes.isEmpty()) {
            LOGGER.warn("[recipes] no runtime recipes collected (KubeJS reload may not have finished)");
            return new CollectResult(Map.of(), 0, 0);
        }

        LOGGER.info("[recipes] sources: getRecipes listed={}, unique from getRecipes={}, added by getAllRecipesFor={}, total={}",
                getRecipesListed, uniqueFromGetRecipes, addedByType, recipes.size());
        logNamespaceRecipeCounts(recipes);

        Map<String, Map<String, JsonElement>> byNamespace = new LinkedHashMap<>();
        int failures = 0;

        for (var entry : recipes.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                JsonObject json = RecipeJsonSerializer.toJson(id, entry.getValue());
                byNamespace.computeIfAbsent(id.getNamespace(), k -> new TreeMap<>()).put(id.toString(), json);
            } catch (Exception e) {
                failures++;
                if (failures <= 30) {
                    LOGGER.warn("[recipes] runtime encode failed {}: {}", id, e.getMessage());
                }
            }
        }

        int collected = byNamespace.values().stream().mapToInt(Map::size).sum();
        LOGGER.info("[recipes] runtime encoded {} recipes, {} failures", collected, failures);
        return new CollectResult(byNamespace, collected, failures);
    }

    /**
     * Merges recipes missing from {@link RecipeManager#getRecipes()}.
     *
     * <p>Forge 1.20.1 {@link RecipeManager#getAllRecipesFor(RecipeType)} returns a {@link java.util.List},
     * not a {@code Map} (Fabric/yarn {@code listAllOfType} naming differs).</p>
     *
     * @return number of recipe ids newly added
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int mergeFromAllRecipeTypes(RecipeManager recipeManager, Map<ResourceLocation, Recipe<?>> into) {
        int before = into.size();
        for (ResourceLocation typeKey : BuiltInRegistries.RECIPE_TYPE.keySet()) {
            RecipeType<?> recipeType = BuiltInRegistries.RECIPE_TYPE.get(typeKey);
            try {
                Object perType = recipeManager.getAllRecipesFor((RecipeType) recipeType);
                mergeRecipesFromPerTypeResult(perType, into);
            } catch (Exception e) {
                LOGGER.error("[recipes] getAllRecipesFor failed for {}: {}", recipeType, e.toString());
            }
        }
        return into.size() - before;
    }

    /** Forge 1.20.1 returns {@link java.util.List}; never cast to {@link Map}. */
    private static void mergeRecipesFromPerTypeResult(Object perType, Map<ResourceLocation, Recipe<?>> into) {
        if (perType instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (!(entry.getValue() instanceof Recipe<?> recipe)) {
                    continue;
                }
                ResourceLocation id = entry.getKey() instanceof ResourceLocation rl ? rl : null;
                if (id == null || ResourceExportFilter.isExcluded(id)) {
                    continue;
                }
                into.putIfAbsent(id, recipe);
            }
            return;
        }
        if (perType instanceof Iterable<?> iterable) {
            for (Object o : iterable) {
                if (!(o instanceof Recipe<?> recipe) || recipe == null) {
                    continue;
                }
                ResourceLocation id = recipe.getId();
                if (id == null || ResourceExportFilter.isExcluded(id)) {
                    continue;
                }
                into.putIfAbsent(id, recipe);
            }
        }
    }

    private static void logNamespaceRecipeCounts(Map<ResourceLocation, Recipe<?>> recipes) {
        for (String ns : WATCH_NAMESPACES) {
            long count = recipes.keySet().stream().filter(id -> ns.equals(id.getNamespace())).count();
            LOGGER.info("[recipes]   {}: {} recipe ids", ns, count);
        }
    }
}
