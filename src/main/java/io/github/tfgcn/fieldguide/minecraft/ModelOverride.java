package io.github.tfgcn.fieldguide.minecraft;

import lombok.Data;

import java.util.Map;

@Data
public class ModelOverride {
    private Map<String, Double> predicate;
    private String model;
}
