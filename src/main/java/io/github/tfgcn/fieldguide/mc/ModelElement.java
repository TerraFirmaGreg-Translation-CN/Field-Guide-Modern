package io.github.tfgcn.fieldguide.mc;

import lombok.Data;

import java.util.Map;

@Data
public class ModelElement {
    private double[] from;
    private double[] to;
    private ElementRotation rotation;
    private Map<String, ElementFace> faces;
    private Boolean shade;
}