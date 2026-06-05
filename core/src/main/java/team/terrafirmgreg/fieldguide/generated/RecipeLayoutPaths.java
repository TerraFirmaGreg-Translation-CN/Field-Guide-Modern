package team.terrafirmgreg.fieldguide.generated;

/**
 * EMI recipe layout paths (under {@link EmiBundlePaths}).
 */
public final class RecipeLayoutPaths {

    /** Layout JSON schema — v2 removes fallback flags; chrome via {@link #CHROME_DIR}. */
    public static final int SCHEMA_VERSION = 2;

    public static final String LAYOUTS_DIR = EmiBundlePaths.RECIPES_DIR;
    public static final String LAYOUT_INDEX_FILE = EmiBundlePaths.RECIPE_INDEX_FILE;

    public static final String CHROME_DIR = EmiBundlePaths.CHROME_DIR;

    public static final String TEXTURES_DIR = EmiBundlePaths.TEXTURES_DIR;
    public static final String TEXTURE_MANIFEST_FILE = EmiBundlePaths.TEXTURE_MANIFEST_FILE;

    private RecipeLayoutPaths() {}

    public static String safeFileName(String recipeId) {
        return RecipeImagePaths.safeFileName(recipeId);
    }

    public static String relativeLayoutJson(String recipeId) {
        return EmiBundlePaths.relativeLayoutJson(recipeId);
    }
}
