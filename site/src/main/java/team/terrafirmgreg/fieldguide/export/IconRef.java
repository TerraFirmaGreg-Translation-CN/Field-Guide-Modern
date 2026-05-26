package team.terrafirmgreg.fieldguide.export;

/**
 * Reference into a generated icon atlas ({@code icons.css} + PNG pages).
 */
public record IconRef(
        String atlasKind,
        String cssClass,
        String registryId,
        int cellSize,
        int page,
        int x,
        int y) {

    public String dataAttribute() {
        return "data-item";
    }

    public String atlasFileName() {
        return "atlas-%03d.png".formatted(page);
    }

    /** Path relative to each language directory, e.g. {@code generated/icons/atlas-000.png}. */
    public String relativeAtlasPath() {
        return "generated/" + atlasKind + "/" + atlasFileName();
    }

    /** CSS for {@code <img src="...atlas.png" style="...">} sprite slice. */
    public String toImgStyle() {
        return "width:%dpx;height:%dpx;image-rendering:pixelated;image-rendering:crisp-edges;"
                .formatted(cellSize, cellSize)
                + "object-fit:none;object-position:-%dpx -%dpx;".formatted(x, y);
    }
}
