package io.github.tfgcn.fieldguide.mc;

import lombok.Data;

@Data
public class ElementFace {
    private String texture;// #id
    private double[] uv;// u1,v1,u2,v2
    private Integer rotation = 0;// 0,90,180,270
    private Integer tintIndex = -1;
    private String cullface;

    public double[] getDefaultUV(String faceName, ModelElement element) {
        if (element.getFrom() == null || element.getTo() == null) {
            return new double[] {0, 0, 16, 16};
        }

        double[] from = element.getFrom();
        double[] to = element.getTo();

        return switch (faceName) {
            case "down", "up" -> new double[]{from[0], from[2], to[0], to[2]};
            case "north", "south" -> new double[]{from[0], from[1], to[0], to[1]};
            case "west", "east" -> new double[]{from[2], from[1], to[2], to[1]};
            default -> new double[]{0, 0, 16, 16};
        };
    }
}