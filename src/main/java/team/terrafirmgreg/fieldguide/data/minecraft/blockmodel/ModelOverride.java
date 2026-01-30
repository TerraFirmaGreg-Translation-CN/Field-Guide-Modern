package team.terrafirmgreg.fieldguide.data.minecraft.blockmodel;

import lombok.Data;

import java.util.Map;

@Data
public class ModelOverride {
    private Map<String, Double> predicate;
    private String model;
}
