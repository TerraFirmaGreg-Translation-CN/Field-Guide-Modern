package team.terrafirmgreg.fieldguide.export;

import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import team.terrafirmgreg.fieldguide.gson.JsonUtils;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Loads Forge runtime export recipe bundles: {@code data/<namespace>/recipes.json}
 * with keys {@code namespace:path/id}.
 */
@Slf4j
public class RecipeBundleLoader {

    private static final TypeToken<Map<String, Object>> BUNDLE_TYPE = new TypeToken<>() {};

    private final Map<String, Map<String, Object>> recipes;

    private RecipeBundleLoader(Map<String, Map<String, Object>> recipes) {
        this.recipes = recipes;
    }

    public static RecipeBundleLoader load(Path exportRoot) {
        Path dataDir = resolveDataDir(exportRoot);
        if (dataDir == null) {
            return new RecipeBundleLoader(Map.of());
        }
        Map<String, Map<String, Object>> merged = new TreeMap<>();
        int files = 0;
        try (var stream = Files.list(dataDir)) {
            for (Path nsDir : stream.filter(Files::isDirectory).sorted().toList()) {
                Path bundle = nsDir.resolve("recipes.json");
                if (!Files.isRegularFile(bundle)) {
                    continue;
                }
                Map<String, Map<String, Object>> chunk = readBundle(bundle);
                if (chunk == null || chunk.isEmpty()) {
                    continue;
                }
                merged.putAll(chunk);
                files++;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan export recipe bundles under " + dataDir, e);
        }
        if (!merged.isEmpty()) {
            log.info("Loaded {} export recipes from {} bundle file(s) under {}", merged.size(), files, dataDir);
        }
        return new RecipeBundleLoader(Collections.unmodifiableMap(merged));
    }

    private static Path resolveDataDir(Path root) {
        if (root == null) {
            return null;
        }
        Path data = root.normalize().toAbsolutePath().resolve("data");
        return Files.isDirectory(data) ? data : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> readBundle(Path bundle) {
        try (Reader reader = Files.newBufferedReader(bundle)) {
            Map<String, Object> raw = JsonUtils.GSON.fromJson(reader, BUNDLE_TYPE.getType());
            if (raw == null) {
                return Map.of();
            }
            Map<String, Map<String, Object>> map = new TreeMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if (e.getValue() instanceof Map<?, ?> m) {
                    Map<String, Object> recipe = new TreeMap<>();
                    m.forEach((k, v) -> recipe.put(String.valueOf(k), v));
                    map.put(e.getKey(), recipe);
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("Failed to read recipe bundle {}", bundle, e);
            return Map.of();
        }
    }

    public int size() {
        return recipes.size();
    }

    public boolean contains(String recipeId) {
        return recipes.containsKey(recipeId);
    }

    public Map<String, Object> get(String recipeId) {
        return recipes.get(recipeId);
    }
}
