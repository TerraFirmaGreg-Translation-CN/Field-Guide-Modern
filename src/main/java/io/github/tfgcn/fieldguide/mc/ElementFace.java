package io.github.tfgcn.fieldguide.mc;

import lombok.Data;

@Data
public class ElementFace {
    private String texture;
    private double[] uv;
    private Integer rotation;
    private String cullface;
    private Integer tintIndex;

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