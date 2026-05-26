package team.terrafirmgreg.fieldguide.asset;

public class ItemStackResult {
    public final ItemImageResult icon;
    public final int count;

    public ItemStackResult(ItemImageResult icon, int count) {
        this.icon = icon;
        this.count = count;
    }

    public String getName() {
        return icon.getName();
    }
}
