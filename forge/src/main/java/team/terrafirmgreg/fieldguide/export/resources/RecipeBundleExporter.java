package team.terrafirmgreg.fieldguide.export.resources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Writes {@code data/&lt;namespace&gt;/recipes.json} — datapack files plus live
 * {@link net.minecraft.world.item.crafting.RecipeManager} entries (KubeJS / GT).
 */
public final class RecipeBundleExporter {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RecipeBundleExporter() {}

    public record Result(
            int namespaces,
            int recipes,
            int datapackRecipes,
            int runtimeRecipes,
            int runtimeEncodeFailures,
            long bytesWritten,
            Set<String> referencedItems,
            Set<String> referencedTags,
            Set<String> referencedFluids,
            Set<String> referencedBlocks,
            int runtimeMerged) {

        public Result(
                int namespaces,
                int recipes,
                int datapackRecipes,
                int runtimeRecipes,
                int runtimeEncodeFailures,
                long bytesWritten) {
            this(namespaces, recipes, datapackRecipes, runtimeRecipes, runtimeEncodeFailures, bytesWritten,
                    Set.of(), Set.of(), Set.of(), Set.of(), 0);
        }
    }

    public static boolean isEnabled() {
        return !Boolean.getBoolean("fieldguide.skipRecipeBundleExport");
    }

    public static Result export(Path outputDir, MinecraftServer server) throws IOException {
        return export(outputDir, server, null);
    }

    /**
     * @param onlyRecipeIds when non-null, only these {@code namespace:path} ids (from {@link team.terrafirmgreg.fieldguide.export.scan.BookScanResult#getRecipes()}).
     */
    public static Result export(Path outputDir, MinecraftServer server, Set<String> onlyRecipeIds) throws IOException {
        ResourceManager rm = server.getResourceManager();
        Path dataRoot = outputDir.resolve("data");

        Map<String, Map<String, JsonElement>> byNamespace = new LinkedHashMap<>();
        int datapackCount = 0;
        Set<String> referencedItems = new TreeSet<>();
        Set<String> referencedTags = new TreeSet<>();
        Set<String> referencedFluids = new TreeSet<>();
        Set<String> referencedBlocks = new TreeSet<>();

        Map<ResourceLocation, Resource> hits = rm.listResources("recipes", loc ->
                !ResourceExportFilter.isExcluded(loc) && loc.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> hit : hits.entrySet()) {
            ResourceLocation fileLoc = hit.getKey();
            String recipeId = toRecipeId(fileLoc);
            if (onlyRecipeIds != null && !onlyRecipeIds.contains(recipeId)) {
                continue;
            }
            String ns = fileLoc.getNamespace();
            try (var reader = new InputStreamReader(hit.getValue().open(), StandardCharsets.UTF_8)) {
                JsonElement recipe = JsonParser.parseReader(reader);
                byNamespace.computeIfAbsent(ns, k -> new TreeMap<>()).put(recipeId, recipe);
                mergeRefs(RecipeReferenceCollector.collectAll(recipe), referencedItems, referencedTags, referencedFluids, referencedBlocks);
                datapackCount++;
            } catch (Exception e) {
                LOGGER.warn("[recipes] datapack read failed {}: {}", fileLoc, e.getMessage());
            }
        }

        RuntimeRecipeCollector.CollectResult runtime = RuntimeRecipeCollector.collect(server);
        int runtimeMerged = 0;
        int runtimeSkippedDatapack = 0;
        for (var nsEntry : runtime.byNamespace().entrySet()) {
            for (var recipeEntry : nsEntry.getValue().entrySet()) {
                if (onlyRecipeIds != null && !onlyRecipeIds.contains(recipeEntry.getKey())) {
                    continue;
                }
                JsonElement incoming = recipeEntry.getValue();
                Map<String, JsonElement> bucket = byNamespace.computeIfAbsent(nsEntry.getKey(), k -> new TreeMap<>());
                JsonElement existing = bucket.get(recipeEntry.getKey());
                if (existing != null && !RecipeJsonSerializer.isRuntimeStub(existing)) {
                    runtimeSkippedDatapack++;
                    continue;
                }
                if (RecipeJsonSerializer.isRuntimeStub(incoming) && existing != null) {
                    runtimeSkippedDatapack++;
                    continue;
                }
                bucket.put(recipeEntry.getKey(), incoming);
                mergeRefs(RecipeReferenceCollector.collectAll(incoming),
                        referencedItems, referencedTags, referencedFluids, referencedBlocks);
                runtimeMerged++;
            }
        }
        if (runtimeSkippedDatapack > 0) {
            LOGGER.info("[recipes] kept {} datapack entries over runtime stubs", runtimeSkippedDatapack);
        }

        int nsCount = 0;
        int recipeCount = 0;
        long bytes = 0;
        int skippedEmptyNamespaces = 0;
        for (var entry : byNamespace.entrySet()) {
            if (entry.getValue().isEmpty()) {
                skippedEmptyNamespaces++;
                continue;
            }
            Path outDir = dataRoot.resolve(entry.getKey());
            Files.createDirectories(outDir);
            Path outFile = outDir.resolve("recipes.json");
            String json = GSON.toJson(entry.getValue());
            Files.writeString(outFile, json);
            nsCount++;
            recipeCount += entry.getValue().size();
            bytes += json.length();
            LOGGER.info("[recipes] {} — {} recipes ({} bytes)", entry.getKey(), entry.getValue().size(), json.length());
        }
        if (skippedEmptyNamespaces > 0) {
            LOGGER.info("[recipes] skipped {} empty namespace bundles", skippedEmptyNamespaces);
        }

        String mode = onlyRecipeIds == null ? "full" : "closure";
        LOGGER.info("[recipes] bundled {} recipes ({}, {} datapack files, {} runtime entries, {} encode failures) across {} namespaces",
                recipeCount, mode, datapackCount, runtime.collected(), runtime.failures(), nsCount);
        if (onlyRecipeIds != null) {
            LOGGER.info("[recipes] closure filter: {} book recipe ids → {} items, {} tags, {} fluids, {} blocks in recipe JSON",
                    onlyRecipeIds.size(), referencedItems.size(), referencedTags.size(),
                    referencedFluids.size(), referencedBlocks.size());
        }

        return new Result(nsCount, recipeCount, datapackCount, runtime.collected(), runtime.failures(), bytes,
                referencedItems, referencedTags, referencedFluids, referencedBlocks, runtimeMerged);
    }

    private static void mergeRefs(
            RecipeReferenceCollector.References refs,
            Set<String> items,
            Set<String> tags,
            Set<String> fluids,
            Set<String> blocks) {
        items.addAll(refs.items());
        tags.addAll(refs.tags());
        fluids.addAll(refs.fluids());
        blocks.addAll(refs.blocks());
    }

    /** {@code gtceu:recipes/foo/bar.json} → {@code gtceu:foo/bar} */
    static String toRecipeId(ResourceLocation fileLoc) {
        String path = fileLoc.getPath();
        if (path.startsWith("recipes/")) {
            path = path.substring("recipes/".length());
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - ".json".length());
        }
        return fileLoc.getNamespace() + ":" + path;
    }
}
