package team.terrafirmgreg.fieldguide.export;

import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import team.terrafirmgreg.fieldguide.gson.JsonUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Reads {@code generated/icons/index.json} (unified 32×32 atlas). Falls back to legacy
 * {@code items/}, {@code block-items/}, {@code fluids/} directories when present.
 */
@Slf4j
public class IconCatalog implements IconLookup {

    private static final TypeToken<Map<String, Object>> INDEX_TYPE = new TypeToken<>() {};

    private static final String UNIFIED_KIND = "icons";

    private final Path exportRoot;
    private final Map<String, SpritePlacement> unified;
    private final Map<String, SpritePlacement> legacyItems;
    private final Map<String, SpritePlacement> legacyBlockItems;
    private final Map<String, SpritePlacement> legacyFluids;
    private final MissingIconReport missingReport;

    public static final String MISSING_ICON_ID = "fieldguide:missing_icon";

    private IconCatalog(
            Path exportRoot,
            Map<String, SpritePlacement> unified,
            Map<String, SpritePlacement> legacyItems,
            Map<String, SpritePlacement> legacyBlockItems,
            Map<String, SpritePlacement> legacyFluids,
            MissingIconReport missingReport) {
        this.exportRoot = exportRoot;
        this.unified = unified;
        this.legacyItems = legacyItems;
        this.legacyBlockItems = legacyBlockItems;
        this.legacyFluids = legacyFluids;
        this.missingReport = missingReport;
    }

    public static IconCatalog load(Path exportRoot) {
        return load(exportRoot, new MissingIconReport());
    }

    public static IconCatalog load(Path exportRoot, MissingIconReport missingReport) {
        Map<String, SpritePlacement> unified = loadAtlas(exportRoot, UNIFIED_KIND);
        boolean useLegacy = unified.isEmpty();
        return new IconCatalog(
                exportRoot,
                unified,
                useLegacy ? loadAtlas(exportRoot, "items") : Map.of(),
                useLegacy ? loadAtlas(exportRoot, "block-items") : Map.of(),
                useLegacy ? loadAtlas(exportRoot, "fluids") : Map.of(),
                missingReport);
    }

    private static Map<String, SpritePlacement> loadAtlas(Path exportRoot, String kind) {
        Path indexFile = exportRoot.resolve("generated").resolve(kind).resolve("index.json");
        if (!Files.isRegularFile(indexFile)) {
            return Map.of();
        }
        try {
            String json = Files.readString(indexFile);
            Map<String, Object> root = JsonUtils.GSON.fromJson(json, INDEX_TYPE.getType());
            if (root == null) {
                return Map.of();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> itemsMap = (Map<String, Object>) root.get("items");
            if (itemsMap == null) {
                return Map.of();
            }
            return SpritePlacement.parseMap(itemsMap, intValue(root.get("cellSize"), 32));
        } catch (IOException e) {
            log.warn("Failed to read icon index {}", indexFile, e);
            return Map.of();
        }
    }

    private static int intValue(Object value, int defaultValue) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }

    @Override
    public Optional<IconRef> resolveItem(String itemId) {
        Optional<IconRef> found = resolveUnified(itemId).or(() -> resolveIn(legacyItems, "items", itemId));
        if (found.isPresent()) {
            return found;
        }
        return resolveUnified(MISSING_ICON_ID);
    }

    @Override
    public Optional<IconRef> resolveBlockItem(String itemId) {
        Optional<IconRef> found = resolveUnified(itemId).or(() -> resolveIn(legacyBlockItems, "block-items", itemId));
        if (found.isPresent()) {
            return found;
        }
        return resolveUnified(MISSING_ICON_ID);
    }

    public Optional<IconRef> resolveAnyItem(String registryId) {
        Optional<IconRef> found = resolveUnified(registryId)
                .or(() -> resolveIn(legacyItems, "items", registryId))
                .or(() -> resolveIn(legacyBlockItems, "block-items", registryId));
        if (found.isPresent()) {
            return found;
        }
        return resolveUnified(MISSING_ICON_ID);
    }

    @Override
    public Optional<IconRef> resolveFluid(String fluidId) {
        Optional<IconRef> found = resolveUnified(fluidId).or(() -> resolveIn(legacyFluids, "fluids", fluidId));
        if (found.isPresent()) {
            return found;
        }
        return resolveUnified(MISSING_ICON_ID);
    }

    public BufferedImage cropSprite(IconRef ref) throws IOException {
        Path atlas = exportRoot.resolve("generated").resolve(ref.atlasKind()).resolve(ref.atlasFileName());
        BufferedImage sheet = ImageIO.read(atlas.toFile());
        int size = ref.cellSize();
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(sheet, 0, 0, size, size, ref.x(), ref.y(), ref.x() + size, ref.y() + size, null);
        return out;
    }

    private Optional<IconRef> resolveUnified(String id) {
        return resolveIn(unified, UNIFIED_KIND, id);
    }

    private Optional<IconRef> resolveIn(Map<String, SpritePlacement> map, String kind, String id) {
        SpritePlacement placement = map.get(id);
        if (placement == null) {
            missingReport.record(id);
            return Optional.empty();
        }
        String cssClass = cssClassFor(kind);
        return Optional.of(new IconRef(kind, cssClass, id, placement.cellSize(), placement.page(), placement.x(), placement.y()));
    }

    private static String cssClassFor(String kind) {
        return switch (kind) {
            case UNIFIED_KIND -> "icon-atlas";
            case "items" -> "item-icon-atlas";
            case "block-items" -> "block-item-icon-atlas";
            case "fluids" -> "fluid-icon-atlas";
            default -> kind + "-icon-atlas";
        };
    }

    public MissingIconReport missingReport() {
        return missingReport;
    }

    public Path generatedDir() {
        return exportRoot.resolve("generated");
    }

    private record SpritePlacement(int cellSize, int page, int x, int y) {

        static Map<String, SpritePlacement> parseMap(Map<String, Object> raw, int defaultCellSize) {
            java.util.Map<String, SpritePlacement> out = new java.util.TreeMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if (e.getValue() instanceof Map<?, ?> m) {
                    int page = number(m.get("page"));
                    int x = number(m.get("x"));
                    int y = number(m.get("y"));
                    out.put(e.getKey(), new SpritePlacement(defaultCellSize, page, x, y));
                }
            }
            return java.util.Collections.unmodifiableMap(out);
        }

        private static int number(Object o) {
            return o instanceof Number n ? n.intValue() : 0;
        }
    }
}
