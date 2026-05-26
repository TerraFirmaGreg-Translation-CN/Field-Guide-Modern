package team.terrafirmgreg.fieldguide.integration.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.RecipesEventJS;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import team.terrafirmgreg.fieldguide.export.resources.KubeJsRecipeJsonCache;

import java.util.Map;

/** Captures KubeJS recipe JSON while {@link RecipesEventJS} is still populated. */
public class FieldGuideKubeJsPlugin extends KubeJSPlugin {

    @Override
    public void injectRuntimeRecipes(
            RecipesEventJS event,
            RecipeManager manager,
            Map<ResourceLocation, Recipe<?>> recipesByName) {
        KubeJsRecipeJsonCache.capture(event);
    }
}
