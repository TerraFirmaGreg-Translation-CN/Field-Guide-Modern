package team.terrafirmgreg.fieldguide.data.minecraft.blockstate;

import lombok.Data;

@Data
public class Variant {
    private String model;// required
    private int x;
    private int y;
    private int z;
    private Boolean uvlock = false;
    private int weight = 1;
}