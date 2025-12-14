package su.terrafirmgreg.fieldguide.data.minecraft.blockstate;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class BlockVariant {
    private String block;
    private Map<String, String> properties;
    private Variant variant;// result
    private List<Variant> variants;// result

    public boolean hasProperties() {
        return properties != null && !properties.isEmpty();
    }
}
