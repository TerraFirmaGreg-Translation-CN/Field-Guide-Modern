package team.terrafirmgreg.fieldguide.asset;

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
}