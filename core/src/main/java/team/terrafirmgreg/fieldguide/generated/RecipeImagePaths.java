package team.terrafirmgreg.fieldguide.generated;

/**
 * Paths for pre-rendered EMI recipe previews under {@code generated/recipes/}.
 * Shared by export (EMI bundle) and :site (HTML).
 */
public final class RecipeImagePaths {

    public static final String GENERATED_DIR = "generated/recipes";
    public static final String INDEX_FILE = "index.json";

    private RecipeImagePaths() {}

    public static String safeFileName(String recipeId) {
        if (recipeId == null || recipeId.isEmpty()) {
            return "unknown";
        }
        return recipeId.replace(':', '_').replace('/', '_');
    }

    public static String relativePng(String recipeId) {
        return safeFileName(recipeId) + ".png";
    }
}
