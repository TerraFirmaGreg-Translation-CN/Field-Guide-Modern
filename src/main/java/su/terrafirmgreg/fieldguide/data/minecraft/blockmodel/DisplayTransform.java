package su.terrafirmgreg.fieldguide.data.minecraft.blockmodel;

import lombok.Data;

@Data
public class DisplayTransform {
    private double[] translation = {0, 0, 0};// [-80, 80], in pixels
    private double[] rotation = {0, 0, 0};// in degrees
    private double[] scale = {1, 1, 1};// [-4,4] by axis
}
