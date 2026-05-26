package team.terrafirmgreg.fieldguide.asset;

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
 * with keys {@code namespace:path/id} (not per-file datapack layout).
 *
 * @deprecated Use {@code team.terrafirmgreg.fieldguide.export.RecipeBundleLoader} in {@code :site}.
 */
@Deprecated
@Slf4j
public class ExportRecipeLoader {

    private static final TypeToken<Map<String, Map<String, Object>>> BUNDLE_TYPE =
            new TypeToken<>() {};

    private final Map<String, Map<String, Object>> recipes;

    private ExportRecipeLoader(Map<String, Map<String, Object>> recipes) {
        this.recipes = recipes;
    }

    /**
     * @param root guide-export root ({@code manifest.json} + {@code data/}) or any dir containing {@code data/}
     */
    public static ExportRecipeLoader tryLoad(Path root) {
        Path dataDir = resolveDataDir(root);
        if (dataDir == null) {
            return null;
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
                for (Map.Entry<String, Map<String, Object>> e : chunk.entrySet()) {
                    merged.put(e.getKey(), e.getValue());
                }
                files++;
            }
        } catch (IOException e) {
            log.warn("Failed to scan export recipe bundles under {}", dataDir, e);
            return null;
        }
        if (merged.isEmpty()) {
            return null;
        }
        log.info("Loaded {} export recipes from {} bundle file(s) under {}", merged.size(), files, dataDir);
        return new ExportRecipeLoader(Collections.unmodifiableMap(merged));
    }

    private static Path resolveDataDir(Path root) {
        if (root == null) {
            return null;
        }
        Path normalized = root.normalize().toAbsolutePath();
        Path data = normalized.resolve("data");
        if (Files.isDirectory(data)) {
            return data;
        }
        return null;
    }

    private static Map<String, Map<String, Object>> readBundle(Path bundle) {
        try (Reader reader = Files.newBufferedReader(bundle)) {
            Map<String, Map<String, Object>> map = JsonUtils.GSON.fromJson(reader, BUNDLE_TYPE.getType());
            return map != null ? map : Map.of();
        } catch (Exception e) {
            log.warn("Failed to read recipe bundle {}", bundle, e);
            return Map.of();
        }
    }

    public boolean isEmpty() {
        return recipes.isEmpty();
    }

    public int size() {
        return recipes.size();
    }

    public Map<String, Object> get(String recipeId) {
        return recipes.get(recipeId);
    }
}
