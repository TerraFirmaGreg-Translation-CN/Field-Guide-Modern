package team.terrafirmgreg.fieldguide.asset;

import lombok.Data;
import team.terrafirmgreg.fieldguide.export.IconRef;

import java.util.List;

@Data
public class ItemImageResult {
    private String name;
    private final String key;

    /** Placeholder / direct PNG path (relative to language dir, no {@code ../}). */
    private final String path;

    /** Export atlas sprites via {@code *-icons.css}; single icon or carousel frames. */
    private final List<IconRef> atlasIcons;

    public ItemImageResult(String path, String name, String key) {
        this(path, name, key, null);
    }

    private ItemImageResult(String path, String name, String key, List<IconRef> atlasIcons) {
        this.path = path;
        this.name = name;
        this.key = key;
        this.atlasIcons = atlasIcons;
    }

    public static ItemImageResult legacy(String path, String name, String key) {
        return new ItemImageResult(path, name, key, null);
    }

    public static ItemImageResult atlas(IconRef ref, String name, String key) {
        return new ItemImageResult(null, name, key, List.of(ref));
    }

    public static ItemImageResult atlasCarousel(List<IconRef> refs, String name, String key) {
        return new ItemImageResult(null, name, key, List.copyOf(refs));
    }

    public boolean isAtlas() {
        return atlasIcons != null && !atlasIcons.isEmpty();
    }

    public boolean isCarousel() {
        return atlasIcons != null && atlasIcons.size() > 1;
    }

    public List<IconRef> getAtlasIcons() {
        return atlasIcons == null ? List.of() : atlasIcons;
    }

    public IconRef primaryAtlas() {
        return getAtlasIcons().get(0);
    }

    /** Path for og:image / preview (site-root relative, e.g. {@code generated/items/atlas-000.png}). */
    public String previewPath() {
        if (isAtlas()) {
            return primaryAtlas().relativeAtlasPath();
        }
        return path;
    }

}
