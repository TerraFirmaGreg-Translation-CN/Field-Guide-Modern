package team.terrafirmgreg.fieldguide.export.resources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import team.terrafirmgreg.fieldguide.generated.EmiBundlePaths;
import team.terrafirmgreg.fieldguide.generated.RecipeLayoutPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Builds {@code emi/items/index.json} from EMI layouts plus {@code index/recipes-by-output.json}.
 * This keeps the renderer demo self-contained without the post-export migration script.
 */
public final class EmiItemsIndexExporter {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private EmiItemsIndexExporter() {}

    public record Result(int itemCount, int inputsIndexed, int outputsIndexed, long indexBytes) {}

    public static boolean isEnabled() {
        return !Boolean.getBoolean("fieldguide.skipEmiItemsIndexExport");
    }

    public static Result export(Path outputDir) throws IOException {
        Path recipeIndexFile = EmiBundlePaths.resolve(outputDir, EmiBundlePaths.RECIPE_INDEX_FILE);
        Path itemsIndexFile = EmiBundlePaths.resolve(outputDir, EmiBundlePaths.ITEMS_INDEX_FILE);
        Path recipesByOutputFile = outputDir.resolve("index").resolve("recipes-by-output.json");

        if (!Files.isRegularFile(recipeIndexFile)) {
            LOGGER.warn("[emi-items] missing {} — skipping items index", recipeIndexFile);
            return new Result(0, 0, 0, 0);
        }

        JsonObject recipeIndex = JsonParser.parseString(Files.readString(recipeIndexFile)).getAsJsonObject();
        JsonObject recipeEntries = recipeIndex.has("recipes") && recipeIndex.get("recipes").isJsonObject()
                ? recipeIndex.getAsJsonObject("recipes")
                : new JsonObject();

        Map<String, Set<String>> inputs = new TreeMap<>();
        Map<String, Set<String>> outputs = new TreeMap<>();

        for (var entry : recipeEntries.entrySet()) {
            String recipeId = entry.getKey();
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject meta = entry.getValue().getAsJsonObject();
            Path layoutPath = resolveLayoutPath(outputDir, recipeId, meta);
            if (!Files.isRegularFile(layoutPath)) {
                continue;
            }
            JsonObject layout = JsonParser.parseString(Files.readString(layoutPath)).getAsJsonObject();
            JsonArray widgets = layout.has("widgets") && layout.get("widgets").isJsonArray()
                    ? layout.getAsJsonArray("widgets")
                    : new JsonArray();
            for (JsonElement widgetEl : widgets) {
                if (!widgetEl.isJsonObject()) {
                    continue;
                }
                JsonObject widget = widgetEl.getAsJsonObject();
                String role = widget.has("role") ? widget.get("role").getAsString() : "";
                Map<String, Set<String>> bucket = "output".equals(role)
                        ? outputs
                        : ("input".equals(role) || "catalyst".equals(role) ? inputs : null);
                if (bucket == null) {
                    continue;
                }
                Set<String> ids = new TreeSet<>();
                if (widget.has("tagDisplayItem") && widget.get("tagDisplayItem").isJsonPrimitive()) {
                    addCanonicalId(widget.get("tagDisplayItem").getAsString(), ids);
                }
                if (widget.has("ingredient")) {
                    collectIngredientIds(widget.get("ingredient"), ids);
                }
                for (String id : ids) {
                    bucket.computeIfAbsent(id, k -> new TreeSet<>()).add(recipeId);
                }
            }
        }

        if (Files.isRegularFile(recipesByOutputFile)) {
            JsonObject byOutputRoot = JsonParser.parseString(Files.readString(recipesByOutputFile)).getAsJsonObject();
            JsonObject entries = byOutputRoot.has("entries") && byOutputRoot.get("entries").isJsonObject()
                    ? byOutputRoot.getAsJsonObject("entries")
                    : new JsonObject();
            for (var entry : entries.entrySet()) {
                String itemId = RecipeIndexExporter.canonicalRegistryId(entry.getKey());
                if (itemId == null || itemId.isBlank() || !entry.getValue().isJsonArray()) {
                    continue;
                }
                for (JsonElement refEl : entry.getValue().getAsJsonArray()) {
                    if (!refEl.isJsonObject()) {
                        continue;
                    }
                    JsonObject ref = refEl.getAsJsonObject();
                    if (ref.has("recipeId") && ref.get("recipeId").isJsonPrimitive()) {
                        outputs.computeIfAbsent(itemId, k -> new TreeSet<>()).add(ref.get("recipeId").getAsString());
                    }
                }
            }
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema", 1);
        root.put("itemCount", 0);
        Map<String, Object> items = new TreeMap<>();
        root.put("items", items);

        int inputRefs = 0;
        int outputRefs = 0;
        Set<String> allIds = new TreeSet<>();
        allIds.addAll(inputs.keySet());
        allIds.addAll(outputs.keySet());
        for (String itemId : allIds) {
            Set<String> inputRecipes = inputs.getOrDefault(itemId, Set.of());
            Set<String> outputRecipes = outputs.getOrDefault(itemId, Set.of());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("inputs", inputRecipes.stream().sorted().toList());
            item.put("outputs", outputRecipes.stream().sorted().toList());
            items.put(itemId, item);
            inputRefs += inputRecipes.size();
            outputRefs += outputRecipes.size();
        }
        root.put("itemCount", items.size());

        Files.createDirectories(itemsIndexFile.getParent());
        String json = GSON.toJson(root);
        Files.writeString(itemsIndexFile, json);
        LOGGER.info("[emi-items] {} items ({} input refs, {} output refs) -> {}",
                items.size(), inputRefs, outputRefs, itemsIndexFile);
        return new Result(items.size(), inputRefs, outputRefs, json.length());
    }

    private static Path resolveLayoutPath(Path outputDir, String recipeId, JsonObject meta) {
        if (meta.has("layout") && meta.get("layout").isJsonPrimitive()) {
            return EmiBundlePaths.resolve(outputDir, meta.get("layout").getAsString());
        }
        return EmiBundlePaths.resolve(
                outputDir,
                RecipeLayoutPaths.LAYOUTS_DIR + "/" + RecipeLayoutPaths.relativeLayoutJson(recipeId));
    }

    private static void collectIngredientIds(JsonElement ingredient, Set<String> out) {
        if (ingredient == null || ingredient.isJsonNull()) {
            return;
        }
        if (ingredient.isJsonPrimitive() && ingredient.getAsJsonPrimitive().isString()) {
            String raw = ingredient.getAsString().trim();
            if (raw.startsWith("item:")) {
                addCanonicalId(raw.substring(5), out);
            } else if (!raw.startsWith("#item:") && raw.contains(":") && !raw.startsWith("#")) {
                addCanonicalId(raw, out);
            }
            return;
        }
        if (ingredient.isJsonArray()) {
            for (JsonElement child : ingredient.getAsJsonArray()) {
                collectIngredientIds(child, out);
            }
            return;
        }
        if (!ingredient.isJsonObject()) {
            return;
        }

        JsonObject obj = ingredient.getAsJsonObject();
        if (obj.has("type") && obj.get("type").isJsonPrimitive() && obj.has("id") && obj.get("id").isJsonPrimitive()) {
            String kind = obj.get("type").getAsString();
            if ("item".equals(kind) || "fluid".equals(kind)) {
                addCanonicalId(obj.get("id").getAsString(), out);
            }
        }
        if (obj.has("entries") && obj.get("entries").isJsonArray()) {
            for (JsonElement entryEl : obj.getAsJsonArray("entries")) {
                if (!entryEl.isJsonObject()) {
                    continue;
                }
                JsonObject entry = entryEl.getAsJsonObject();
                if (entry.has("ids") && entry.get("ids").isJsonArray()) {
                    for (JsonElement idEl : entry.getAsJsonArray("ids")) {
                        if (idEl.isJsonPrimitive()) {
                            addCanonicalId(idEl.getAsString(), out);
                        }
                    }
                }
                if (entry.has("fluid") && entry.get("fluid").isJsonObject()) {
                    JsonObject fluid = entry.getAsJsonObject("fluid");
                    if (fluid.has("id") && fluid.get("id").isJsonPrimitive()) {
                        addCanonicalId(fluid.get("id").getAsString(), out);
                    }
                }
            }
        }
    }

    private static void addCanonicalId(String raw, Set<String> out) {
        String id = RecipeIndexExporter.canonicalRegistryId(raw);
        if (id != null && !id.isBlank()) {
            out.add(id);
        }
    }
}
