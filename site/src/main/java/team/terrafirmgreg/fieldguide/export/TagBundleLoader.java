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
 * Loads export tag bundles: {@code data/<namespace>/tags/{items,blocks,fluids}.json}.
 */
@Slf4j
public class TagBundleLoader {

    private static final TypeToken<Map<String, Object>> BUNDLE_TYPE = new TypeToken<>() {};

    private static final String[] TAG_FILES = {"items.json", "blocks.json", "fluids.json"};

    private final Map<String, Object> tags;

    private TagBundleLoader(Map<String, Object> tags) {
        this.tags = tags;
    }

    public static TagBundleLoader load(Path exportRoot) {
        Path dataDir = exportRoot.resolve("data");
        if (!Files.isDirectory(dataDir)) {
            return new TagBundleLoader(Map.of());
        }
        Map<String, Object> merged = new TreeMap<>();
        int files = 0;
        try (var nsStream = Files.list(dataDir)) {
            for (Path nsDir : nsStream.filter(Files::isDirectory).sorted().toList()) {
                Path tagsDir = nsDir.resolve("tags");
                if (!Files.isDirectory(tagsDir)) {
                    continue;
                }
                for (String tagFile : TAG_FILES) {
                    Path bundle = tagsDir.resolve(tagFile);
                    if (!Files.isRegularFile(bundle)) {
                        continue;
                    }
                    Map<String, Object> chunk = readBundle(bundle);
                    if (!chunk.isEmpty()) {
                        merged.putAll(chunk);
                        files++;
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan export tag bundles under " + dataDir, e);
        }
        if (!merged.isEmpty()) {
            log.info("Loaded {} export tags from {} bundle file(s)", merged.size(), files);
        }
        return new TagBundleLoader(Collections.unmodifiableMap(merged));
    }

    private static Map<String, Object> readBundle(Path bundle) {
        try (Reader reader = Files.newBufferedReader(bundle)) {
            Map<String, Object> map = JsonUtils.GSON.fromJson(reader, BUNDLE_TYPE.getType());
            return map != null ? map : Map.of();
        } catch (Exception e) {
            log.warn("Failed to read tag bundle {}", bundle, e);
            return Map.of();
        }
    }

    public int size() {
        return tags.size();
    }

    public Object get(String tagId) {
        return tags.get(tagId);
    }

    public boolean contains(String tagId) {
        return tags.containsKey(tagId);
    }
}
