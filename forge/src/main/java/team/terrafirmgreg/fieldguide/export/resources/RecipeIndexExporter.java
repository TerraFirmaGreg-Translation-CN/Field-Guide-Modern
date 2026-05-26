package team.terrafirmgreg.fieldguide.export.resources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Builds {@code index/recipes-by-output.json} from bundled {@code data/&lt;ns&gt;/recipes.json}.
 * Runs at the end of forge export.
 */
public final class RecipeIndexExporter {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RecipeIndexExporter() {}

    public record Result(int outputItems, int recipeRefs, long indexBytes) {}

    public record RecipeRef(String namespace, String recipeId) {}

    public static boolean isEnabled() {
        return !Boolean.getBoolean("fieldguide.skipRecipeIndexExport");
    }

    public static Result export(Path outputDir) throws IOException {
        Path dataRoot = outputDir.resolve("data");
        Path indexRoot = outputDir.resolve("index");
        Files.createDirectories(indexRoot);

        Map<String, List<RecipeRef>> byOutput = new TreeMap<>();

        if (!Files.isDirectory(dataRoot)) {
            LOGGER.warn("[index] no data/ directory — skipping recipe index");
            return new Result(0, 0, 0);
        }

        int recipeRefs = 0;
        try (var nsStream = Files.list(dataRoot)) {
            for (Path nsDir : nsStream.filter(Files::isDirectory).sorted().toList()) {
                Path recipesFile = nsDir.resolve("recipes.json");
                if (!Files.isRegularFile(recipesFile)) {
                    continue;
                }
                String namespace = nsDir.getFileName().toString();
                JsonObject bundle = JsonParser.parseString(Files.readString(recipesFile)).getAsJsonObject();
                for (var entry : bundle.entrySet()) {
                    String recipeId = entry.getKey();
                    try {
                        Set<String> outputs = new LinkedHashSet<>();
                        collectItemOutputs(entry.getValue(), outputs);
                        for (String itemId : outputs) {
                            byOutput.computeIfAbsent(itemId, k -> new ArrayList<>())
                                    .add(new RecipeRef(namespace, recipeId));
                            recipeRefs++;
                        }
                    } catch (Exception e) {
                        LOGGER.warn("[index] skip {}:{} — {}", namespace, recipeId, e.toString());
                    }
                }
            }
        }

        Map<String, Object> index = new LinkedHashMap<>();
        index.put("schema", 1);
        index.put("description", "item id -> list of { namespace, recipeId } in data/<namespace>/recipes.json");
        index.put("entries", byOutput);

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schema", 1);
        manifest.put("outputItemCount", byOutput.size());
        manifest.put("recipeRefCount", recipeRefs);
        List<String> bundleFiles = new ArrayList<>();
        try (var nsStream = Files.list(dataRoot)) {
            for (Path nsDir : nsStream.filter(Files::isDirectory).sorted().toList()) {
                if (Files.isRegularFile(nsDir.resolve("recipes.json"))) {
                    bundleFiles.add(nsDir.getFileName() + "/recipes.json");
                }
            }
        }
        manifest.put("recipeBundles", bundleFiles);

        Path byOutputFile = indexRoot.resolve("recipes-by-output.json");
        Path manifestFile = indexRoot.resolve("manifest.json");
        String byOutputJson = GSON.toJson(index);
        String manifestJson = GSON.toJson(manifest);
        Files.writeString(byOutputFile, byOutputJson);
        Files.writeString(manifestFile, manifestJson);

        LOGGER.info("[index] recipes-by-output: {} items, {} recipe refs ({} bytes)",
                byOutput.size(), recipeRefs, byOutputJson.length());

        return new Result(byOutput.size(), recipeRefs, byOutputJson.length() + manifestJson.length());
    }

    private static void collectItemOutputs(JsonElement recipe, Set<String> out) {
        if (recipe == null || recipe.isJsonNull()) {
            return;
        }
        if (recipe.isJsonObject()) {
            JsonObject obj = recipe.getAsJsonObject();
            if (obj.has("result")) {
                extractItemId(obj.get("result"), out);
            }
            if (obj.has("output")) {
                extractItemId(obj.get("output"), out);
            }
            for (String key : List.of("outputItems", "outputItem", "itemOutputs", "results")) {
                if (obj.has(key)) {
                    extractItemId(obj.get(key), out);
                }
            }
            for (var entry : obj.entrySet()) {
                if (!"result".equals(entry.getKey()) && !"output".equals(entry.getKey())) {
                    collectItemOutputs(entry.getValue(), out);
                }
            }
        } else if (recipe.isJsonArray()) {
            for (JsonElement el : recipe.getAsJsonArray()) {
                collectItemOutputs(el, out);
            }
        }
    }

    private static void extractItemId(JsonElement el, Set<String> out) {
        if (el == null || el.isJsonNull()) {
            return;
        }
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            normalizeItemString(el.getAsString(), out);
            return;
        }
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            // GT Content / ingredient stacks: nested objects, not plain strings.
            for (String key : List.of("item", "id", "base", "content")) {
                if (obj.has(key)) {
                    extractItemId(obj.get(key), out);
                }
            }
            return;
        }
        if (el.isJsonArray()) {
            for (JsonElement child : el.getAsJsonArray()) {
                extractItemId(child, out);
            }
        }
    }

    /** Supports {@code minecraft:iron_ingot}, {@code 2x gtceu:steel_ingot}, etc. */
    private static void normalizeItemString(String raw, Set<String> out) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String s = raw.trim();
        int space = s.indexOf(' ');
        if (space > 0 && s.charAt(0) >= '0' && s.charAt(0) <= '9') {
            s = s.substring(space + 1).trim();
        }
        if (s.startsWith("#")) {
            return;
        }
        if (s.contains(":")) {
            out.add(canonicalRegistryId(s));
        }
    }

    /** Plain id for output indices: strips SNBT and {@code @nbtHash} variant suffixes. */
    static String canonicalRegistryId(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw;
        if (s.startsWith("item:")) {
            s = s.substring(5);
        }
        int brace = s.indexOf('{');
        if (brace >= 0) {
            s = s.substring(0, brace);
        }
        int at = s.indexOf('@');
        if (at >= 0) {
            s = s.substring(0, at);
        }
        return s;
    }
}
