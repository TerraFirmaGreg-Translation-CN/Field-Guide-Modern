package io.github.tfgcn.fieldguide;

import lombok.Data;

@Data
public class FluidResult {
    public final String path;
    public final String name;
    
    public FluidResult(String path, String name) {
        this.path = path;
        this.name = name;
    }
}