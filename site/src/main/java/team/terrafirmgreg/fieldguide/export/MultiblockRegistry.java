package team.terrafirmgreg.fieldguide.export;

import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import team.terrafirmgreg.fieldguide.gson.JsonUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves Patchouli multiblock ids from {@code meta.json} {@code multiblockDefs} (runtime registry).
 * Does not look up mods jars.
 */
@Slf4j
public class MultiblockRegistry {

    private static final TypeToken<Map<String, Object>> META_TYPE = new TypeToken<>() {};
    private static final TypeToken<List<Map<String, Object>>> DEF_LIST_TYPE = new TypeToken<>() {};

    private final Map<String, ResolvedMultiblock> byId;

    private MultiblockRegistry(Map<String, ResolvedMultiblock> byId) {
        this.byId = byId;
    }

    public static MultiblockRegistry load(Path exportRoot) {
        Path metaFile = exportRoot.resolve("meta.json");
        if (!Files.isRegularFile(metaFile)) {
            return new MultiblockRegistry(Map.of());
        }
        try {
            String json = Files.readString(metaFile);
            Map<String, Object> meta = JsonUtils.GSON.fromJson(json, META_TYPE.getType());
            if (meta == null) {
                return new MultiblockRegistry(Map.of());
            }
            Object defs = meta.get("multiblockDefs");
            if (defs == null) {
                return new MultiblockRegistry(Map.of());
            }
            String defsJson = JsonUtils.GSON.toJson(defs);
            List<Map<String, Object>> list = JsonUtils.GSON.fromJson(defsJson, DEF_LIST_TYPE.getType());
            if (list == null) {
                return new MultiblockRegistry(Map.of());
            }
            Map<String, ResolvedMultiblock> map = new LinkedHashMap<>();
            for (Map<String, Object> entry : list) {
                ResolvedMultiblock resolved = fromDefEntry(entry);
                if (resolved.id() != null) {
                    map.put(resolved.id(), resolved);
                }
            }
            log.info("Loaded {} multiblock defs from meta.json", map.size());
            return new MultiblockRegistry(Collections.unmodifiableMap(map));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + metaFile, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static ResolvedMultiblock fromDefEntry(Map<String, Object> entry) {
        String id = stringVal(entry.get("id"));
        String source = stringVal(entry.get("source"));
        String error = stringVal(entry.get("error"));
        List<List<String>> pattern = patternFromEntry(entry.get("pattern"));
        Map<String, Map<String, Object>> exportMapping = exportMappingFromEntry(entry.get("mapping"));
        Map<String, String> flatMapping = flatMappingFromExport(exportMapping);
        List<Map<String, Object>> blockstates = blockstatesFromEntry(entry.get("blockstates"));
        return new ResolvedMultiblock(id, source, pattern, flatMapping, exportMapping, blockstates, error);
    }

    @SuppressWarnings("unchecked")
    private static List<List<String>> patternFromEntry(Object patternObj) {
        if (!(patternObj instanceof List<?> layers)) {
            return List.of();
        }
        List<List<String>> out = new ArrayList<>();
        for (Object layer : layers) {
            if (layer instanceof List<?> rows) {
                List<String> zRows = new ArrayList<>();
                for (Object row : rows) {
                    if (row != null) {
                        zRows.add(row.toString());
                    }
                }
                out.add(List.copyOf(zRows));
            } else if (layer != null) {
                out.add(List.of(layer.toString()));
            }
        }
        return List.copyOf(out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> exportMappingFromEntry(Object mappingObj) {
        if (!(mappingObj instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Map<String, Object>> mapping = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String key = e.getKey().toString();
            if (e.getValue() instanceof Map<?, ?> valueMap) {
                Map<String, Object> copy = new LinkedHashMap<>();
                valueMap.forEach((k, v) -> copy.put(String.valueOf(k), v));
                mapping.put(key, Map.copyOf(copy));
            } else {
                Map<String, Object> refOnly = new LinkedHashMap<>();
                refOnly.put("ref", e.getValue().toString());
                mapping.put(key, Map.copyOf(refOnly));
            }
        }
        return Map.copyOf(mapping);
    }

    private static Map<String, String> flatMappingFromExport(Map<String, Map<String, Object>> exportMapping) {
        Map<String, String> flat = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : exportMapping.entrySet()) {
            Object ref = e.getValue().get("ref");
            if (ref != null) {
                flat.put(e.getKey(), ref.toString());
            }
        }
        return Map.copyOf(flat);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> blockstatesFromEntry(Object blockstatesObj) {
        if (!(blockstatesObj instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                Map<String, Object> copy = new LinkedHashMap<>();
                m.forEach((k, v) -> copy.put(String.valueOf(k), v));
                out.add(Map.copyOf(copy));
            }
        }
        return List.copyOf(out);
    }

    private static String stringVal(Object o) {
        return o == null ? null : o.toString();
    }

    public Optional<ResolvedMultiblock> resolve(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Fallback when a Patchouli page embeds pattern/mapping directly (no registry id).
     */
    public Optional<ResolvedMultiblock> resolveFromPage(
            String id,
            List<String> pattern,
            Map<String, String> mapping) {
        if (pattern == null || pattern.isEmpty()) {
            return Optional.empty();
        }
        List<List<String>> layers = List.of(List.copyOf(pattern));
        return Optional.of(new ResolvedMultiblock(
                id,
                "page_embedded",
                layers,
                mapping != null ? Map.copyOf(mapping) : Map.of(),
                Map.of(),
                List.of(),
                null));
    }
}
