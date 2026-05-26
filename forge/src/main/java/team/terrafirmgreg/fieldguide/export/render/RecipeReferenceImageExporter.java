package team.terrafirmgreg.fieldguide.export.render;

import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import team.terrafirmgreg.fieldguide.generated.RecipeImagePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Optional full-panel PNG snapshots for human QA only — not used by emi.js rendering.
 */
public final class RecipeReferenceImageExporter {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");
    private static final int EMI_RECIPE_MARGIN = 8;
    private static final int EXPORT_SCALE = Math.max(1,
            Integer.getInteger("fieldguide.recipeImageScale", 2));
    private static final int LOG_EVERY = 25;

    private RecipeReferenceImageExporter() {}

    public record Result(int requested, int written, int missing, int failures, long pngBytes) {}

    /** On by default when EMI layout export runs; opt out with {@code -Dfieldguide.exportRecipeReferencePng=false}. */
    public static boolean isEnabled() {
        if (Boolean.getBoolean("fieldguide.skipRecipeReferencePng")) {
            return false;
        }
        String prop = System.getProperty("fieldguide.exportRecipeReferencePng");
        if (prop != null) {
            return !"false".equalsIgnoreCase(prop);
        }
        return EmiRecipeLayoutExporter.isEnabled();
    }

    public static boolean isEmiAvailable() {
        return EmiRecipeResolver.isEmiAvailable();
    }

    public static Result export(Path outputDir, Minecraft client, Set<String> recipeIds) throws IOException {
        Path recipesRoot = outputDir.resolve(RecipeImagePaths.GENERATED_DIR);
        Files.createDirectories(recipesRoot);
        if (Files.isDirectory(recipesRoot)) {
            try (var stream = Files.list(recipesRoot)) {
                for (Path p : stream.toList()) {
                    String name = p.getFileName().toString();
                    if (name.endsWith(".png") || RecipeImagePaths.INDEX_FILE.equals(name)) {
                        Files.deleteIfExists(p);
                    }
                }
            }
        }

        Map<String, String> index = new TreeMap<>();
        int written = 0;
        int missing = 0;
        int failures = 0;
        long pngBytes = 0;
        int total = recipeIds.size();
        int n = 0;

        for (String recipeId : recipeIds) {
            n++;
            EmiRecipe recipe = EmiRecipeResolver.resolve(recipeId);
            if (recipe == null) {
                missing++;
                continue;
            }
            try {
                String fileName = RecipeImagePaths.relativePng(recipeId);
                Path out = recipesRoot.resolve(fileName);
                long bytes = renderRecipePng(client, recipe, out);
                index.put(recipeId, fileName);
                pngBytes += bytes;
                written++;
                if (n % LOG_EVERY == 0 || n == total) {
                    LOGGER.info("[recipe-reference] {}/{} — {} ok", n, total, written);
                }
            } catch (Exception e) {
                failures++;
                LOGGER.warn("[recipe-reference] failed for {}: {}", recipeId, e.toString());
            }
        }

        if (!index.isEmpty()) {
            Map<String, Object> root = new TreeMap<>();
            root.put("schema", 1);
            root.put("purpose", "reference_only");
            root.put("recipes", index);
            Files.writeString(recipesRoot.resolve(RecipeImagePaths.INDEX_FILE), new com.google.gson.GsonBuilder()
                    .setPrettyPrinting().create().toJson(root));
        }

        LOGGER.info("[recipe-reference] done: {}/{} written ({} bytes)", written, total, pngBytes);
        return new Result(total, written, missing, failures, pngBytes);
    }

    private static long renderRecipePng(Minecraft client, EmiRecipe recipe, Path out) throws IOException {
        int w = Math.max(1, recipe.getDisplayWidth());
        int h = Math.max(1, recipe.getDisplayHeight());
        int logicalW = w + EMI_RECIPE_MARGIN;
        int logicalH = h + EMI_RECIPE_MARGIN;
        int pixelW = logicalW * EXPORT_SCALE;
        int pixelH = logicalH * EXPORT_SCALE;

        try (OffScreenRenderer off = new OffScreenRenderer(pixelW, pixelH)) {
            GuiGraphics graphics = new GuiGraphics(client, client.renderBuffers().bufferSource());
            off.captureAsPng(() -> off.runWithEmiRecipeMatrices(logicalW, logicalH, () -> {
                EmiRenderHelper.renderRecipe(recipe, EmiDrawContext.wrap(graphics), 0, 0, false, -1);
                graphics.flush();
            }), out);
        }
        return Files.size(out);
    }
}
