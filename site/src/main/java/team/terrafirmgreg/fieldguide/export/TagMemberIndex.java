package team.terrafirmgreg.fieldguide.export;

import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import team.terrafirmgreg.fieldguide.gson.JsonUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Runtime-expanded tag members from {@code index/tag-members.json}.
 */
@Slf4j
public class TagMemberIndex {

    private static final TypeToken<Map<String, Object>> ROOT_TYPE = new TypeToken<>() {};
    private static final TypeToken<Map<String, List<String>>> SECTION_TYPE = new TypeToken<>() {};

    private final Map<String, List<String>> itemMembers;
    private final Map<String, List<String>> blockMembers;
    private final Map<String, List<String>> fluidMembers;

    private TagMemberIndex(
            Map<String, List<String>> itemMembers,
            Map<String, List<String>> blockMembers,
            Map<String, List<String>> fluidMembers) {
        this.itemMembers = itemMembers;
        this.blockMembers = blockMembers;
        this.fluidMembers = fluidMembers;
    }

    public static TagMemberIndex load(Path exportRoot) {
        Path indexFile = exportRoot.resolve("index/tag-members.json");
        if (!Files.isRegularFile(indexFile)) {
            log.debug("No tag-members index at {}", indexFile);
            return empty();
        }
        try {
            String json = Files.readString(indexFile);
            Map<String, Object> root = JsonUtils.GSON.fromJson(json, ROOT_TYPE.getType());
            if (root == null) {
                return empty();
            }
            return new TagMemberIndex(
                    parseSection(root.get("items")),
                    parseSection(root.get("blocks")),
                    parseSection(root.get("fluids")));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + indexFile, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> parseSection(Object section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, List<String>> map = JsonUtils.GSON.fromJson(
                JsonUtils.GSON.toJson(section), SECTION_TYPE.getType());
        if (map == null || map.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(map);
    }

    private static TagMemberIndex empty() {
        return new TagMemberIndex(Map.of(), Map.of(), Map.of());
    }

    public List<String> getItemMembers(String tagId) {
        return itemMembers.getOrDefault(tagId, List.of());
    }

    public List<String> getBlockMembers(String tagId) {
        return blockMembers.getOrDefault(tagId, List.of());
    }

    public List<String> getFluidMembers(String tagId) {
        return fluidMembers.getOrDefault(tagId, List.of());
    }
}
