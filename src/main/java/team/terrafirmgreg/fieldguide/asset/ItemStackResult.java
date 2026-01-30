package team.terrafirmgreg.fieldguide.asset;

import lombok.Data;

/**
 * 物品堆结果类
 */
@Data
public class ItemStackResult {
    public final String path;
    public final String name;
    public final int count;
    
    public ItemStackResult(String path, String name, int count) {
        this.path = path;
        this.name = name;
        this.count = count;
    }
}