package team.terrafirmgreg.fieldguide.export;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves handbook / Patchouli blockstate refs using export {@code meta.json} blockstates and tag members.
 */
@Slf4j
public final class BlockstateRefResolver {

    private final ExportAssetAccess assets;
    private final TagMemberIndex tagMembers;

    public BlockstateRefResolver(ExportAssetAccess assets, TagMemberIndex tagMembers) {
        this.assets = assets;
        this.tagMembers = tagMembers;
    }

    /**
     * Model / blockstate id suitable for {@link team.terrafirmgreg.fieldguide.render.Multiblock3DRenderer}.
     */
    public String resolveModelId(String ref) {
        if (ref == null || ref.isBlank()) {
            return ref;
        }
        String trimmed = ref.trim();
        if ("AIR".equalsIgnoreCase(trimmed) || "air".equalsIgnoreCase(trimmed)) {
            return "minecraft:air";
        }

        Optional<Map<String, Object>> meta = assets.resolvedBlockstate(trimmed);
        if (meta.isEmpty()) {
            return trimmed;
        }
        Map<String, Object> entry = meta.get();
        Object override = entry.get("override");
        if (override instanceof String s && !s.isBlank()) {
            return s;
        }
        Object kind = entry.get("kind");
        if ("tag".equals(kind)) {
            String tagId = entry.get("tag") instanceof String t ? t : trimmed.startsWith("#") ? trimmed.substring(1) : trimmed;
            List<String> blocks = tagMembers.getBlockMembers(tagId);
            if (!blocks.isEmpty()) {
                return blocks.get(0);
            }
            log.debug("No block tag members for {} (ref {})", tagId, trimmed);
        }
        if ("air".equals(kind)) {
            return "minecraft:air";
        }
        return trimmed;
    }

    public Map<String, String> resolveMapping(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> e : raw.entrySet()) {
            if (e.getKey() == null || e.getKey().isEmpty()) {
                continue;
            }
            out.put(e.getKey(), resolveModelId(e.getValue()));
        }
        return Map.copyOf(out);
    }

    public Map<String, String> resolveExportMapping(Map<String, Map<String, Object>> exportMapping) {
        if (exportMapping == null || exportMapping.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : exportMapping.entrySet()) {
            if (e.getKey() == null || e.getKey().isEmpty() || e.getValue() == null) {
                continue;
            }
            Object ref = e.getValue().get("ref");
            out.put(e.getKey(), resolveModelId(ref != null ? ref.toString() : null));
        }
        return Map.copyOf(out);
    }
}
