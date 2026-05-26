package team.terrafirmgreg.fieldguide.export.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans compact atlas page dimensions from sprite count instead of always allocating
 * {@code maxAtlasSize}×{@code maxAtlasSize} buffers.
 */
public final class IconAtlasLayout {

    private IconAtlasLayout() {}

    public record PagePlan(int cols, int rows) {
        public int widthPx(int cellSize) {
            return cols * cellSize;
        }

        public int heightPx(int cellSize) {
            return rows * cellSize;
        }

        public int capacity() {
            return cols * rows;
        }
    }

    /**
     * Square-ish grid per page: {@code cols ≈ ceil(sqrt(n))}, capped by {@code maxAtlasSize / cellSize}.
     * Example: 797 sprites at 16px → 29×29 cells → 464×464px first page (not 2048×2048).
     */
    public static List<PagePlan> plan(int spriteCount, int cellSize, int maxAtlasSize) {
        if (spriteCount <= 0) {
            return List.of(new PagePlan(1, 1));
        }
        int maxCols = Math.max(1, maxAtlasSize / cellSize);
        int maxRows = Math.max(1, maxAtlasSize / cellSize);
        int maxPerPage = maxCols * maxRows;

        List<PagePlan> pages = new ArrayList<>();
        int remaining = spriteCount;
        while (remaining > 0) {
            int onPage = Math.min(remaining, maxPerPage);
            int cols = (int) Math.ceil(Math.sqrt(onPage));
            int rows = (int) Math.ceil((double) onPage / cols);
            if (cols > maxCols) {
                cols = maxCols;
                rows = (int) Math.ceil((double) onPage / cols);
            }
            if (rows > maxRows) {
                rows = maxRows;
                cols = (int) Math.ceil((double) onPage / rows);
                cols = Math.min(cols, maxCols);
            }
            pages.add(new PagePlan(cols, rows));
            remaining -= cols * rows;
        }
        return pages;
    }
}
