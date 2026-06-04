package team.terrafirmgreg.fieldguide.export;

import java.util.List;
import java.util.Map;

/**
 * Multiblock structure from export {@code meta.multiblockDefs} or embedded Patchouli page JSON.
 */
public record ResolvedMultiblock(
        String id,
        String source,
        List<List<String>> pattern,
        Map<String, String> mapping,
        Map<String, Map<String, Object>> exportMapping,
        List<Map<String, Object>> blockstates,
        String error) {

    public boolean isOk() {
        return error == null
                && (!exportMapping().isEmpty() || (mapping != null && !mapping.isEmpty()));
    }

    public boolean hasPattern() {
        return pattern != null && !pattern.isEmpty();
    }

    public String[][] toPatternArray() {
        if (pattern == null || pattern.isEmpty()) {
            return new String[0][];
        }
        String[][] out = new String[pattern.size()][];
        for (int i = 0; i < pattern.size(); i++) {
            List<String> layer = pattern.get(i);
            out[i] = layer != null ? layer.toArray(String[]::new) : new String[0];
        }
        return out;
    }
}
