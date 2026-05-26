package team.terrafirmgreg.fieldguide.export.resources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Writes merged runtime tags: {@code data/&lt;ns&gt;/tags/items.json}, {@code blocks.json}, {@code fluids.json}.
 *
 * <p>Uses {@link net.minecraft.server.MinecraftServer#registryAccess()} after reload, so KubeJS
 * {@code ServerEvents.TAGS} add/remove/replace are included (not datapack JSON alone).</p>
 */
public final class TagBundleExporter {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private TagBundleExporter() {}

    public record Result(int namespaces, int tagsWritten, long bytesWritten) {}

    public static boolean isEnabled() {
        return !Boolean.getBoolean("fieldguide.skipTagBundleExport");
    }

    public static Result export(Path outputDir, MinecraftServer server) throws IOException {
        return export(outputDir, server, null);
    }

    /** @param onlyTagIds when non-null, only these {@code namespace:path} tag ids (closure). */
    public static Result export(Path outputDir, MinecraftServer server, Set<String> onlyTagIds) throws IOException {
        var access = server.registryAccess();
        Path dataRoot = outputDir.resolve("data");

        Map<String, Map<String, JsonObject>> itemTags = new LinkedHashMap<>();
        Map<String, Map<String, JsonObject>> blockTags = new LinkedHashMap<>();
        Map<String, Map<String, JsonObject>> fluidTags = new LinkedHashMap<>();

        collectTags(access.registryOrThrow(Registries.ITEM), itemTags, onlyTagIds);
        collectTags(access.registryOrThrow(Registries.BLOCK), blockTags, onlyTagIds);
        collectTags(access.registryOrThrow(Registries.FLUID), fluidTags, onlyTagIds);

        Set<String> namespaces = new HashSet<>();
        namespaces.addAll(itemTags.keySet());
        namespaces.addAll(blockTags.keySet());
        namespaces.addAll(fluidTags.keySet());

        long bytes = 0;
        int tagCount = 0;
        tagCount += countTags(itemTags);
        tagCount += countTags(blockTags);
        tagCount += countTags(fluidTags);

        bytes += writeTagFiles(dataRoot, "tags/items.json", itemTags);
        bytes += writeTagFiles(dataRoot, "tags/blocks.json", blockTags);
        bytes += writeTagFiles(dataRoot, "tags/fluids.json", fluidTags);

        String mode = onlyTagIds == null ? "full" : "closure";
        LOGGER.info("[tags] bundled {} tags across {} namespaces ({} bytes, {})", tagCount, namespaces.size(), bytes, mode);
        logWatchNamespaceTagCounts(itemTags, blockTags, fluidTags);
        return new Result(namespaces.size(), tagCount, bytes);
    }

    /** Runtime registry tags (includes KubeJS {@code ServerEvents.TAGS} add/remove/replace). */
    private static void logWatchNamespaceTagCounts(
            Map<String, Map<String, JsonObject>> itemTags,
            Map<String, Map<String, JsonObject>> blockTags,
            Map<String, Map<String, JsonObject>> fluidTags) {
        for (String ns : new String[] {"tfg", "gtceu", "kubejs"}) {
            int items = itemTags.getOrDefault(ns, Map.of()).size();
            int blocks = blockTags.getOrDefault(ns, Map.of()).size();
            int fluids = fluidTags.getOrDefault(ns, Map.of()).size();
            LOGGER.info("[tags]   {}: {} item tags, {} block tags, {} fluid tags", ns, items, blocks, fluids);
        }
    }

    private static <T> void collectTags(
            Registry<T> registry,
            Map<String, Map<String, JsonObject>> out,
            Set<String> onlyTagIds) {
        registry.getTags().forEach(pair -> {
            TagKey<T> tagKey = pair.getFirst();
            ResourceLocation tagId = tagKey.location();
            if (ResourceExportFilter.isExcluded(tagId)) {
                return;
            }
            String key = tagId.toString();
            if (onlyTagIds != null && !onlyTagIds.contains(key)) {
                return;
            }
            List<String> values = new ArrayList<>();
            pair.getSecond().stream()
                    .map(Holder::unwrapKey)
                    .flatMap(java.util.Optional::stream)
                    .map(ResourceKey::location)
                    .map(ResourceLocation::toString)
                    .sorted()
                    .forEach(values::add);

            JsonObject tagJson = new JsonObject();
            JsonArray arr = new JsonArray();
            values.forEach(arr::add);
            tagJson.add("values", arr);

            String ns = tagId.getNamespace();
            out.computeIfAbsent(ns, k -> new TreeMap<>()).put(key, tagJson);
        });
    }

    private static long writeTagFiles(
            Path dataRoot,
            String fileName,
            Map<String, Map<String, JsonObject>> byNamespace) throws IOException {
        long bytes = 0;
        for (var entry : byNamespace.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            Path outFile = dataRoot.resolve(entry.getKey()).resolve(fileName);
            Files.createDirectories(outFile.getParent());
            String json = GSON.toJson(entry.getValue());
            Files.writeString(outFile, json);
            bytes += json.length();
            LOGGER.info("[tags] {}/{} — {} tags ({} bytes)", entry.getKey(), fileName, entry.getValue().size(), json.length());
        }
        return bytes;
    }

    private static int countTags(Map<String, Map<String, JsonObject>> byNamespace) {
        return byNamespace.values().stream().mapToInt(Map::size).sum();
    }
}
