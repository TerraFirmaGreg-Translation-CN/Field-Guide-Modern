package team.terrafirmgreg.fieldguide.export;

import java.util.List;
import java.util.Map;

/**
 * Multiblock structure from export {@code meta.multiblockDefs} or embedded Patchouli page JSON.
 */
public record ResolvedMultiblock(
        String id,
        String source,
        List<String> pattern,
        Map<String, String> mapping,
        List<Map<String, Object>> blockstates,
        String error) {

    public boolean isOk() {
        return error == null && pattern != null && !pattern.isEmpty();
    }
}
