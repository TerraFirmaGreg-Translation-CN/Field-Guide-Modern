package io.github.tfgcn.fieldguide.asset;

import lombok.Data;

@Data
public class FluidImageResult {

    private final String path;
    private String name;
    private final String key;// translation key

    public FluidImageResult(String path, String name, String key) {
        this.path = path;
        this.name = name;
        this.key = key;
    }
}
