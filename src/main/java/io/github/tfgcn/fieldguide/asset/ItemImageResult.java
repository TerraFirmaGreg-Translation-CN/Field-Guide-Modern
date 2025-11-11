package io.github.tfgcn.fieldguide.asset;

import lombok.Data;

@Data
public class ItemImageResult {
    private final String path;
    private String name;
    private final String key;// translation key
    
    public ItemImageResult(String path, String name, String key) {
        this.path = path;
        this.name = name;
        this.key = key;
    }

    @Override
    public String toString() {
        return String.format("ItemImage[path=%s, name=%s, key=%s]", path, name, key);
    }
}