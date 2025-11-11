package io.github.tfgcn.fieldguide.mc;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.Map;

@Data
public class ModelElement {
    private double[] from;
    private double[] to;
    private ElementRotation rotation;
    private Map<String, ElementFace> faces;
    private Boolean shade;
    @SerializedName("light_emission")
    private Integer lightEmission;// [0, 15]
}