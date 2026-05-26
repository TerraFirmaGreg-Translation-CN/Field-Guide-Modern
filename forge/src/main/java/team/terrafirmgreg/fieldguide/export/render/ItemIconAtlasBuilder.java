package team.terrafirmgreg.fieldguide.export.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Packs item icons (default 16×16, in-game slot size) into PNG atlases plus {@code index.json} and {@code items-icons.css}.
 * Atlas pages stay in memory until {@link #finish()} writes all PNGs, index, and CSS in one pass.
 */
final class ItemIconAtlasBuilder implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final int cellSize;
    private final int maxAtlasSize;
    private final Path outputDir;
    private final String cssClass;
    private final String cssFileName;
    private final List<IconAtlasLayout.PagePlan> pagePlans;
    private final Map<String, Integer> usageWeights;

    private final List<PageInfo> pages = new ArrayList<>();
    private final Map<String, SpriteRef> items = new LinkedHashMap<>();

    private NativeImage currentPage;
    private int pageIndex;
    private int pageWidth;
    private int pageHeight;
    private int cursorX;
    private int cursorY;
    private int rowHeight;
    /** Cropped PNG must cover all placed sprites, not only the last row's {@link #cursorX}. */
    private int extentX;
    private int extentY;
    private int pagePlanIndex;

    ItemIconAtlasBuilder(Path outputDir, int cellSize, int maxAtlasSize, String atlasKind) {
        this(outputDir, cellSize, maxAtlasSize, atlasKind, List.of(), null);
    }

    ItemIconAtlasBuilder(
            Path outputDir,
            int cellSize,
            int maxAtlasSize,
            String atlasKind,
            List<IconAtlasLayout.PagePlan> pagePlans,
            Map<String, Integer> usageWeights) {
        this.outputDir = outputDir;
        this.cellSize = cellSize;
        this.maxAtlasSize = maxAtlasSize;
        this.pagePlans = pagePlans != null ? List.copyOf(pagePlans) : List.of();
        this.usageWeights = usageWeights;
        if ("icon".equals(atlasKind)) {
            this.cssClass = "icon-atlas";
            this.cssFileName = "icons.css";
        } else {
            this.cssClass = atlasKind + "-icon-atlas";
            this.cssFileName = atlasKind + "-icons.css";
        }
        startNewPage();
    }

    void place(String itemId, OffScreenRenderer frame) {
        if (cursorX + cellSize > pageWidth) {
            nextRow();
        }
        if (cursorY + cellSize > pageHeight) {
            flushPageToMemory();
            startNewPage();
            if (cursorX + cellSize > pageWidth) {
                nextRow();
            }
        }

        frame.copyPixelsTo(currentPage, cursorX, cursorY);
        int usage = usageWeights != null ? usageWeights.getOrDefault(itemId, 0) : 0;
        items.put(itemId, new SpriteRef(pageIndex, cursorX, cursorY, usage));
        cursorX += cellSize;
        extentX = Math.max(extentX, cursorX);
        extentY = Math.max(extentY, cursorY + cellSize);
    }

    record AtlasResult(int itemsPlaced, int pageCount, long indexBytes, long pngBytes, long cssBytes) {}

    AtlasResult finish() throws IOException {
        if (currentPage != null && (!items.isEmpty() || cursorX > 0 || cursorY > 0)) {
            flushPageToMemory();
        } else if (currentPage != null) {
            currentPage.close();
            currentPage = null;
        }

        Files.createDirectories(outputDir);

        long pngBytes = 0;
        for (PageInfo page : pages) {
            Path out = outputDir.resolve(page.fileName());
            page.image().writeToFile(out);
            pngBytes += Files.size(out);
            page.image().close();
        }

        Map<String, Object> indexRoot = new LinkedHashMap<>();
        indexRoot.put("schema", 1);
        indexRoot.put("cellSize", cellSize);
        if (usageWeights != null && !usageWeights.isEmpty()) {
            indexRoot.put("sort", "usageDesc");
        }
        List<Map<String, Object>> pageList = new ArrayList<>();
        for (PageInfo p : pages) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("file", p.fileName());
            entry.put("width", p.width());
            entry.put("height", p.height());
            pageList.add(entry);
        }
        indexRoot.put("pages", pageList);
        indexRoot.put("items", items);

        Path indexPath = outputDir.resolve("index.json");
        String indexJson = GSON.toJson(indexRoot);
        Files.writeString(indexPath, indexJson);

        Path cssPath = outputDir.resolve(cssFileName);
        String css = buildCss();
        Files.writeString(cssPath, css, StandardCharsets.UTF_8);

        LOGGER.info("[item-icons] atlas: {} items, {} pages, index {} bytes, png {} bytes, css {} bytes",
                items.size(), pages.size(), indexJson.length(), pngBytes, css.length());

        return new AtlasResult(items.size(), pages.size(), indexJson.length(), pngBytes, css.length());
    }

    @Override
    public void close() {
        if (currentPage != null) {
            currentPage.close();
            currentPage = null;
        }
        for (PageInfo page : pages) {
            page.image().close();
        }
        pages.clear();
    }

    private void nextRow() {
        extentX = Math.max(extentX, pageWidth);
        cursorX = 0;
        cursorY += rowHeight;
        rowHeight = cellSize;
    }

    private void startNewPage() {
        if (pagePlanIndex < pagePlans.size()) {
            IconAtlasLayout.PagePlan plan = pagePlans.get(pagePlanIndex);
            pageWidth = plan.widthPx(cellSize);
            pageHeight = plan.heightPx(cellSize);
            pagePlanIndex++;
        } else {
            pageWidth = maxAtlasSize;
            pageHeight = maxAtlasSize;
        }
        currentPage = new NativeImage(pageWidth, pageHeight, true);
        cursorX = 0;
        cursorY = 0;
        rowHeight = cellSize;
        extentX = 0;
        extentY = 0;
    }

    /** Finishes the in-progress page and keeps the cropped bitmap in {@link #pages} until {@link #finish()}. */
    private void flushPageToMemory() {
        if (currentPage == null) {
            return;
        }
        int usedWidth = Math.max(extentX, cursorX);
        int usedHeight = Math.max(extentY, cursorY + (cursorX > 0 ? rowHeight : 0));
        if (usedWidth <= 0) {
            usedWidth = cellSize;
        }
        if (usedHeight <= 0) {
            usedHeight = cellSize;
        }
        usedWidth = Math.min(usedWidth, pageWidth);
        usedHeight = Math.min(usedHeight, pageHeight);

        String fileName = "atlas-%03d.png".formatted(pageIndex);
        NativeImage trimmed = cropToUsed(currentPage, usedWidth, usedHeight);
        pages.add(new PageInfo(pageIndex, fileName, usedWidth, usedHeight, trimmed));

        currentPage.close();
        currentPage = null;
        pageIndex++;
    }

    private static NativeImage cropToUsed(NativeImage source, int width, int height) {
        NativeImage cropped = new NativeImage(width, height, true);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                cropped.setPixelRGBA(x, y, source.getPixelRGBA(x, y));
            }
        }
        return cropped;
    }

    private String buildCss() {
        var sb = new StringBuilder();
        sb.append("/* Auto-generated item icon sprites. cell=").append(cellSize).append("px */\n");
        sb.append('.').append(cssClass).append(" {\n");
        sb.append("  width: ").append(cellSize).append("px;\n");
        sb.append("  height: ").append(cellSize).append("px;\n");
        sb.append("  image-rendering: pixelated;\n");
        sb.append("  image-rendering: crisp-edges;\n");
        sb.append("  background-repeat: no-repeat;\n");
        sb.append("  display: inline-block;\n");
        sb.append("}\n\n");

        for (var entry : items.entrySet()) {
            SpriteRef ref = entry.getValue();
            PageInfo page = pages.get(ref.page());
            sb.append('.').append(cssClass).append("[data-item=\"")
                    .append(escapeCssAttr(entry.getKey()))
                    .append("\"] {\n");
            sb.append("  background-image: url('")
                    .append(page.fileName())
                    .append("');\n");
            sb.append("  background-position: -")
                    .append(ref.x())
                    .append("px -")
                    .append(ref.y())
                    .append("px;\n");
            sb.append("}\n\n");
        }
        return sb.toString();
    }

    private static String escapeCssAttr(String id) {
        return id.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record SpriteRef(int page, int x, int y, int usage) {}

    private record PageInfo(int index, String fileName, int width, int height, NativeImage image) {}
}
