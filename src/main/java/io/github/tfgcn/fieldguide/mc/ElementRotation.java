package io.github.tfgcn.fieldguide.mc;

import lombok.Data;

@Data
public class ElementRotation {
    private double[] origin;
    private String axis;// "x", "y", "z"
    private Double angle;
    private Boolean rescale;
}
