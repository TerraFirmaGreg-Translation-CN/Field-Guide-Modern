package io.github.tfgcn.fieldguide.minecraft;

import lombok.Data;

@Data
public class Variant {
    public String model;// required

    public int x;

    public int y;

    public Boolean uvlock;

    public int weight = 1;
}