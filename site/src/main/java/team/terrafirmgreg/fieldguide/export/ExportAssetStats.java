package team.terrafirmgreg.fieldguide.export;

import lombok.Data;

import java.util.Set;
import java.util.TreeSet;

@Data
public class ExportAssetStats {
    private final Set<String> missingTextures = new TreeSet<>();

    public void addMissingTexture(String id) {
        missingTextures.add(id);
    }
}
