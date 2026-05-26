package team.terrafirmgreg.fieldguide.export.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import team.terrafirmgreg.fieldguide.generated.RecipeLayoutPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.item.ItemStack;

/**
 * Exports EMI recipe widget layouts as JSON for emi.js ({@link RecipeLayoutPaths}).
 * Chrome for RootWidget / DrawableWidget is content-addressed under {@link RecipeLayoutPaths#CHROME_DIR}.
 */
public final class EmiRecipeLayoutExporter {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final int PANEL_MARGIN = 4;
    private static final int LOG_EVERY = 25;

    private EmiRecipeLayoutExporter() {}

    public record Result(
            int requested,
            int written,
            int missing,
            int failures,
            int chromeLayers,
            int chromeDeduped,
            int uniqueChromeFiles,
            long jsonBytes,
            long chromeBytes,
            Set<String> referencedItems,
            Set<String> referencedFluids,
            Set<String> referencedTags,
            Map<String, ItemStack> iconVariants,
            RecipeTextureExporter.Result textures) {}

    public static boolean isEnabled() {
        if (Boolean.getBoolean("fieldguide.skipEmiLayoutExport")) {
            return false;
        }
        String prop = System.getProperty("fieldguide.exportEmiLayout");
        if (prop != null) {
            return !"false".equalsIgnoreCase(prop);
        }
        return true;
    }

    public static int layoutScale() {
        return Math.max(1, Integer.getInteger("fieldguide.recipeLayoutScale", 2));
    }

    public static Result export(Path outputDir, Minecraft client, Set<String> recipeIds) throws IOException {
        Path layoutsRoot = outputDir.resolve(RecipeLayoutPaths.LAYOUTS_DIR);
        Path chromeRoot = outputDir.resolve(RecipeLayoutPaths.CHROME_DIR);
        Files.createDirectories(layoutsRoot);
        Files.createDirectories(chromeRoot);

        Set<String> textureIds = new TreeSet<>();
        Set<String> referencedItems = new TreeSet<>();
        Set<String> referencedFluids = new TreeSet<>();
        Set<String> referencedTags = new TreeSet<>();
        Map<String, ItemStack> iconVariants = new LinkedHashMap<>();
        Map<String, String> chromeHashToRelative = new ConcurrentHashMap<>();
        Map<String, Object> indexEntries = new TreeMap<>();
        int written = 0;
        int missing = 0;
        int failures = 0;
        int chromeLayers = 0;
        int chromeDeduped = 0;
        long jsonBytes = 0;
        int total = recipeIds.size();
        int n = 0;

        for (String recipeId : recipeIds) {
            n++;
            EmiRecipe recipe = EmiRecipeResolver.resolve(recipeId);
            if (recipe == null) {
                missing++;
                if (n % LOG_EVERY == 0 || n == total) {
                    LOGGER.warn("[emi-layout] {}/{} — {} missing so far", n, total, missing);
                }
                continue;
            }
            try {
                int[] chromeWritten = {0};
                int[] chromeDedupedCount = {0};
                JsonObject layout = buildLayout(
                        client, recipe, recipeId, textureIds, referencedItems, referencedFluids, referencedTags,
                        iconVariants, chromeRoot, chromeHashToRelative, chromeWritten, chromeDedupedCount);
                chromeLayers += chromeWritten[0];
                chromeDeduped += chromeDedupedCount[0];

                String fileName = RecipeLayoutPaths.relativeLayoutJson(recipeId);
                Path out = layoutsRoot.resolve(fileName);
                String json = GSON.toJson(layout);
                Files.writeString(out, json);
                jsonBytes += json.length();

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("layout", RecipeLayoutPaths.LAYOUTS_DIR + "/" + fileName);
                if (layout.has("category")) {
                    entry.put("category", layout.get("category").getAsString());
                }
                indexEntries.put(recipeId, entry);

                written++;
                if (n % LOG_EVERY == 0 || n == total) {
                    LOGGER.info("[emi-layout] {}/{} — {} ok, {} missing, {} fail",
                            n, total, written, missing, failures);
                }
            } catch (Exception e) {
                failures++;
                LOGGER.warn("[emi-layout] failed for {}: {}", recipeId, e.toString());
            }
        }

        Map<String, Object> indexRoot = new LinkedHashMap<>();
        indexRoot.put("schema", RecipeLayoutPaths.SCHEMA_VERSION);
        indexRoot.put("scale", layoutScale());
        indexRoot.put("recipes", indexEntries);
        Files.writeString(
                outputDir.resolve(RecipeLayoutPaths.LAYOUT_INDEX_FILE),
                GSON.toJson(indexRoot));

        RecipeTextureExporter.Result textures =
                RecipeTextureExporter.export(outputDir, client, textureIds);

        long chromeBytes = dirSize(chromeRoot);
        LOGGER.info("[emi-layout] done: {}/{} layouts ({} json bytes), {} missing, {} failed, "
                        + "chrome layers {} ({} deduped), {} unique files, {} chrome bytes",
                written, total, jsonBytes, missing, failures,
                chromeLayers, chromeDeduped, chromeHashToRelative.size(), chromeBytes);
        return new Result(
                total, written, missing, failures,
                chromeLayers, chromeDeduped, chromeHashToRelative.size(),
                jsonBytes, chromeBytes, referencedItems, referencedFluids, referencedTags, iconVariants, textures);
    }

    private static JsonObject buildLayout(
            Minecraft client,
            EmiRecipe recipe,
            String bookId,
            Set<String> textureIds,
            Set<String> referencedItems,
            Set<String> referencedFluids,
            Set<String> referencedTags,
            Map<String, ItemStack> iconVariants,
            Path chromeRoot,
            Map<String, String> chromeHashToRelative,
            int[] chromeWritten,
            int[] chromeDedupedCount) {
        int displayW = Math.max(1, recipe.getDisplayWidth());
        int displayH = Math.max(1, recipe.getDisplayHeight());

        List<Widget> widgets = new ArrayList<>();
        WidgetHolder holder = new WidgetHolder() {
            @Override
            public int getWidth() {
                return displayW;
            }

            @Override
            public int getHeight() {
                return displayH;
            }

            @Override
            public <T extends Widget> T add(T widget) {
                widgets.add(widget);
                return widget;
            }
        };
        recipe.addWidgets(holder);

        JsonArray widgetArray = new JsonArray();
        EmiWidgetSerializer.Context ctx = new EmiWidgetSerializer.Context(
                client, chromeRoot, chromeHashToRelative, chromeWritten, chromeDedupedCount,
                referencedItems, referencedFluids, referencedTags, iconVariants);
        EmiWidgetSerializer.serializeWidgets(recipe, widgets, textureIds, ctx, widgetArray::add);

        JsonObject panel = new JsonObject();
        panel.addProperty("width", displayW);
        panel.addProperty("height", displayH);
        panel.addProperty("margin", PANEL_MARGIN);
        panel.addProperty("frameWidth", displayW + PANEL_MARGIN * 2);
        panel.addProperty("frameHeight", displayH + PANEL_MARGIN * 2);

        JsonObject root = new JsonObject();
        root.addProperty("schema", RecipeLayoutPaths.SCHEMA_VERSION);
        root.addProperty("id", bookId);
        root.addProperty("scale", layoutScale());
        root.add("panel", panel);

        ResourceLocation emiId = recipe.getId();
        if (emiId != null) {
            root.addProperty("emiId", emiId.toString());
        }
        if (recipe.getCategory() != null && recipe.getCategory().getId() != null) {
            root.addProperty("category", recipe.getCategory().getId().toString());
        }

        root.add("widgets", widgetArray);

        return root;
    }

    private static long dirSize(Path root) {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        try (var walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        } catch (IOException e) {
            return 0;
        }
    }
}
