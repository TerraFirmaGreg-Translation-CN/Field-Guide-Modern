package team.terrafirmgreg.fieldguide.export.render;

/**
 * Icon atlas cell sizes for runtime export (override via system properties).
 */
public final class IconExportSizes {

    /** Default matches on-site 32×32 display (no CSS scale hacks). */
    private static final int DEFAULT_ICON = 32;
    /** Max PNG page edge; 2048 is safe for WebGL1/mobile. */
    private static final int DEFAULT_ATLAS_MAX = 2048;

    private IconExportSizes() {}

    public static int iconCellSize() {
        Integer unified = Integer.getInteger("fieldguide.iconSize");
        if (unified != null) {
            return boundedSize(unified, "fieldguide.iconSize");
        }
        if (System.getProperty("fieldguide.itemIconSize") != null) {
            return boundedSize(Integer.getInteger("fieldguide.itemIconSize"), "fieldguide.itemIconSize");
        }
        if (System.getProperty("fieldguide.blockItemIconSize") != null) {
            return boundedSize(Integer.getInteger("fieldguide.blockItemIconSize"), "fieldguide.blockItemIconSize");
        }
        if (System.getProperty("fieldguide.fluidIconSize") != null) {
            return boundedSize(Integer.getInteger("fieldguide.fluidIconSize"), "fieldguide.fluidIconSize");
        }
        return DEFAULT_ICON;
    }

    /** @deprecated use {@link #iconCellSize()} */
    @Deprecated
    public static int itemIconSize() {
        return iconCellSize();
    }

    /** @deprecated use {@link #iconCellSize()} */
    @Deprecated
    public static int blockItemIconSize() {
        return iconCellSize();
    }

    /** @deprecated use {@link #iconCellSize()} */
    @Deprecated
    public static int fluidIconSize() {
        return iconCellSize();
    }

    public static int atlasMaxSize() {
        int size = Integer.getInteger("fieldguide.itemIconAtlasMaxSize", DEFAULT_ATLAS_MAX);
        if (size < 256 || size > 8192) {
            throw new IllegalArgumentException("fieldguide.itemIconAtlasMaxSize must be 256..8192, got " + size);
        }
        return size;
    }

    private static int boundedSize(int size, String property) {
        if (size < 8 || size > 256) {
            throw new IllegalArgumentException(property + " must be 8..256, got " + size);
        }
        return size;
    }
}
