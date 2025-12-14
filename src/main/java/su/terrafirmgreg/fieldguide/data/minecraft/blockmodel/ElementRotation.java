package su.terrafirmgreg.fieldguide.data.minecraft.blockmodel;

import lombok.Data;

@Data
public class ElementRotation {
    private double[] origin;
    private String axis;// "x", "y", "z"
    private Double angle;// [-45, 45]
    private Boolean rescale = false;
}
