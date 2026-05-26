package team.terrafirmgreg.fieldguide.export.render;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.resources.ResourceLocation;

/**
 * Resolves book recipe ids to {@link EmiRecipe} (direct id, TMRV synthetic id, or linear scan).
 */
public final class EmiRecipeResolver {

    private EmiRecipeResolver() {}

    public static boolean isEmiAvailable() {
        try {
            Class.forName("dev.emi.emi.api.EmiApi");
            return EmiApi.getRecipeManager() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static EmiRecipe resolve(String bookId) {
        var manager = EmiApi.getRecipeManager();
        if (manager == null) {
            return null;
        }
        ResourceLocation direct = ResourceLocation.parse(bookId);
        EmiRecipe found = manager.getRecipe(direct);
        if (found != null) {
            return found;
        }
        ResourceLocation tmrv = ResourceLocation.fromNamespaceAndPath(
                "toomanyrecipeviewers", "/" + bookId.replace(':', '/'));
        found = manager.getRecipe(tmrv);
        if (found != null) {
            return found;
        }
        for (EmiRecipe recipe : manager.getRecipes()) {
            ResourceLocation id = recipe.getId();
            if (id == null) {
                continue;
            }
            if (bookId.equals(id.toString())) {
                return recipe;
            }
            if ("toomanyrecipeviewers".equals(id.getNamespace())
                    && bookId.equals(id.getPath().substring(1).replace('/', ':'))) {
                return recipe;
            }
        }
        return null;
    }
}
