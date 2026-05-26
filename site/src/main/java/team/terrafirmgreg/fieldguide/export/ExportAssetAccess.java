package team.terrafirmgreg.fieldguide.export;

import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import team.terrafirmgreg.fieldguide.gson.JsonUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Asset reads from export {@code assets/} plus resolved blockstates from {@code meta.json}.
 */
@Getter
public class ExportAssetAccess {

    private static final TypeToken<Map<String, Object>> META_TYPE = new TypeToken<>() {};
    private static final TypeToken<List<Map<String, Object>>> BS_LIST_TYPE = new TypeToken<>() {};

    private final Path exportRoot;
    private final ExportModelLoader models;
    private final Map<String, Map<String, Object>> blockstateByRef;

    public ExportAssetAccess(Path exportRoot, ExportModelLoader models) {
        this.exportRoot = exportRoot.normalize().toAbsolutePath();
        this.models = models;
        this.blockstateByRef = loadBlockstateIndex(this.exportRoot.resolve("meta.json"));
    }

    public FsAssetSource assets() {
        return models.getSource();
    }

    private static Map<String, Map<String, Object>> loadBlockstateIndex(Path metaFile) {
        if (!Files.isRegularFile(metaFile)) {
            return Map.of();
        }
        try {
            Map<String, Object> meta = JsonUtils.GSON.fromJson(Files.readString(metaFile), META_TYPE.getType());
            if (meta == null) {
                return Map.of();
            }
            Object blockstates = meta.get("blockstates");
            if (blockstates == null) {
                return Map.of();
            }
            List<Map<String, Object>> list = JsonUtils.GSON.fromJson(
                    JsonUtils.GSON.toJson(blockstates), BS_LIST_TYPE.getType());
            if (list == null) {
                return Map.of();
            }
            Map<String, Map<String, Object>> index = new LinkedHashMap<>();
            for (Map<String, Object> entry : list) {
                Object ref = entry.get("ref");
                if (ref != null) {
                    index.put(ref.toString(), entry);
                }
            }
            return Map.copyOf(index);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read meta blockstates from " + metaFile, e);
        }
    }

    public Optional<Map<String, Object>> resolvedBlockstate(String ref) {
        return Optional.ofNullable(blockstateByRef.get(ref));
    }

    public boolean assetExists(String resourcePath) {
        return models.getSource().exists(resourcePath);
    }
}
