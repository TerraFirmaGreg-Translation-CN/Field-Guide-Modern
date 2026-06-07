package team.terrafirmgreg.fieldguide.render;

import team.terrafirmgreg.fieldguide.export.ExportAssetKey;
import team.terrafirmgreg.fieldguide.export.ExportModelLoader;
import team.terrafirmgreg.fieldguide.export.MultiblockRenderResolver;
import team.terrafirmgreg.fieldguide.asset.ItemImageResult;
import team.terrafirmgreg.fieldguide.data.patchouli.page.PageMultiblock;
import team.terrafirmgreg.fieldguide.data.patchouli.page.PageMultiblockData;
import team.terrafirmgreg.fieldguide.data.tfc.page.PageMultiMultiblock;
import team.terrafirmgreg.fieldguide.data.tfc.page.TFCMultiblockData;
import team.terrafirmgreg.fieldguide.exception.InternalException;
import team.terrafirmgreg.fieldguide.export.GlTFExporter;
import team.terrafirmgreg.fieldguide.export.IconCatalog;
import team.terrafirmgreg.fieldguide.export.IconRef;
import team.terrafirmgreg.fieldguide.localization.I18n;
import team.terrafirmgreg.fieldguide.localization.LocalizationManager;
import team.terrafirmgreg.fieldguide.render3d.scene.Node;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@Slf4j
public class TextureRenderer {

    private final ExportModelLoader loader;
    private final IconCatalog iconCatalog;
    private final LocalizationManager localizationManager;
    private final MultiblockSceneBuilder multiblockSceneBuilder;
    private final MultiblockRenderResolver multiblockResolver;

    private final Map<String, String> IMAGE_CACHE = new HashMap<>();
    private final Map<String, ItemImageResult> itemImageCache = new HashMap<>();
    private final Map<String, Integer> lastUid = new HashMap<>();

    private static final Map<String, String> GLB_CACHE = new HashMap<>();

    public TextureRenderer(ExportModelLoader loader, LocalizationManager localizationManager, IconCatalog iconCatalog) {
        this(loader, localizationManager, iconCatalog, null);
    }

    public TextureRenderer(
            ExportModelLoader loader,
            LocalizationManager localizationManager,
            IconCatalog iconCatalog,
            MultiblockRenderResolver multiblockResolver) {
        this.loader = loader;
        this.iconCatalog = iconCatalog;
        this.localizationManager = localizationManager;
        this.multiblockResolver = multiblockResolver;
        this.multiblockSceneBuilder = new MultiblockSceneBuilder(new BlockStateModelBuilder(loader));
        lastUid.put("image", 0);
    }

    public String nextId(String prefix) {
        int count = lastUid.getOrDefault(prefix, 0) + 1;
        lastUid.put(prefix, count);
        return prefix + count;
    }

    /**
     * Resolves handbook icons from {@code guide-export/assets/icons/} (FGE atlas).
     */
    public ItemImageResult getItemImage(String item, boolean placeholder) {
        if (item.endsWith(".png")) {
            return new ItemImageResult(convertIcon(item), null, null);
        }

        if (item.startsWith("tag:")) {
            item = "#" + item.substring(4);
            log.info("tag: {}", item);
        }

        if (itemImageCache.containsKey(item)) {
            ItemImageResult result = itemImageCache.get(item);
            if (result.getKey() != null) {
                result.setName(localizationManager.translate("item." + result.getKey(), "block." + result.getKey()));
            }
            return result;
        }

        int nbtIndex = item.indexOf('{');
        if (nbtIndex > 0) {
            log.warn("Item with NBT: {}", item);
            item = item.substring(0, nbtIndex);
        }

        String name = null;
        String key = null;
        List<String> items;

        if (item.startsWith("#")) {
            name = localizationManager.translateWithArgs(I18n.TAG, item);
            items = loader.loadItemTag(item.substring(1));
        } else if (item.contains(",")) {
            items = Arrays.asList(item.split(","));
        } else {
            items = Collections.singletonList(item);
        }

        if (items.size() == 1) {
            int index = item.indexOf(':');
            if (index > 0) {
                String namespace = item.substring(0, index);
                if (namespace.startsWith("#")) {
                    namespace = namespace.substring(1);
                }
                localizationManager.lazyLoadNamespace(namespace);
            }
            key = items.get(0).replace('/', '.').replace(':', '.');
            name = localizationManager.translate("item." + key, "block." + key);
        }

        return resolveExportIcons(item, items, name, key, placeholder);
    }

    private ItemImageResult resolveExportIcons(
            String cacheKey,
            List<String> registryIds,
            String name,
            String key,
            boolean placeholder) {
        List<IconRef> refs = new ArrayList<>();
        for (String id : registryIds) {
            iconCatalog.resolveAnyItem(id).ifPresentOrElse(refs::add, () -> log.warn("No export icon for {}", id));
        }

        if (refs.isEmpty()) {
            log.error("No export icons for: {}", cacheKey);
            if (placeholder) {
                ItemImageResult fallback = new ItemImageResult("_images/placeholder_64.png", name, null);
                itemImageCache.put(cacheKey, fallback);
                return fallback;
            }
            throw new InternalException("No export icon for: " + cacheKey);
        }

        try {
            ItemImageResult result =
                    refs.size() == 1
                            ? ItemImageResult.atlas(refs.get(0), name, key)
                            : ItemImageResult.atlasCarousel(refs, name, key);
            if (cacheKey.startsWith("#")) {
                result = result.withTagClickId(cacheKey.substring(1));
            }
            itemImageCache.put(cacheKey, result);
            return result;
        } catch (Exception e) {
            log.error("Failed to assemble export icons for {}", cacheKey, e);
            if (placeholder) {
                ItemImageResult fallback = new ItemImageResult("_images/placeholder_64.png", name, null);
                itemImageCache.put(cacheKey, fallback);
                return fallback;
            }
            throw new InternalException("Failed to create item image: " + cacheKey);
        }
    }

    /** {@code patchouli:image} — crop/resize book textures for static HTML. */
    public String convertImage(String image) {
        if (IMAGE_CACHE.containsKey(image)) {
            return IMAGE_CACHE.get(image);
        }

        try {
            ExportAssetKey assetKey = loader.getTextureKey(image);
            BufferedImage img = loader.loadTexture(assetKey);

            int width = img.getWidth();
            int height = img.getHeight();

            if (width != height) {
                log.warn("Image is not square. Automatically resizing, but there may be losses. ({} x {}): {}", width, height, image);
            }

            if (width % 256 != 0) {
                log.warn("Image size is not a multiple of 256. Automatically resizing, but there may be losses. ({} x {}): {}",
                        width, height, image);
                img = resizeImage(img, 400, 400);
            }

            if (width != height || width % 256 != 0) {
                log.warn("Image size is not square or multiple of 256. Need to resize. ({} x {}): {}", width, height, image);
            }

            int size = width * 200 / 256;
            BufferedImage cropped = img.getSubimage(0, 0, size, size);

            if (size != 400) {
                cropped = resizeImage(cropped, 400, 400);
            }

            nextId("image");
            String ref = saveImage(assetKey.getResourcePath(), cropped);
            IMAGE_CACHE.put(image, ref);
            return ref;
        } catch (Exception e) {
            throw new InternalException("Failed to convert image: " + image + " - " + e.getMessage());
        }
    }

    public String convertIcon(String image) {
        if (IMAGE_CACHE.containsKey(image)) {
            return IMAGE_CACHE.get(image);
        }

        try {
            ExportAssetKey assetKey = new ExportAssetKey(image, null, "assets", ".png");
            BufferedImage img = loader.loadTexture(assetKey);
            int width = img.getWidth();
            int height = img.getHeight();

            if (width != 16 || height != 16) {
                log.warn("Icon must be 16x16: {} ({} x {})", image, width, height);
                throw new InternalException("Icon must be 16x16: " + image);
            }

            BufferedImage resized = resizeImage(img, 64, 64);
            String ref = saveImage(assetKey.getResourcePath(), resized);
            IMAGE_CACHE.put(image, ref);
            return ref;
        } catch (Exception e) {
            throw new InternalException("Failed to convert icon: " + image + " - " + e.getMessage());
        }
    }

    public String saveImage(String path, BufferedImage image) {
        File outputFile = loader.getOutputDir().resolve(path).toFile();
        try {
            FileUtils.createParentDirectories(outputFile);
            ImageIO.write(image, "png", outputFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save image: " + outputFile.getAbsolutePath(), e);
        }
        return path;
    }

    public List<String> generateMultiMultiblockGLB(PageMultiMultiblock data) throws Exception {
        if (multiblockResolver != null) {
            List<String> paths = new ArrayList<>();
            for (PageMultiblockData resolved : multiblockResolver.resolve(data)) {
                paths.add(generateMultiblockGLB(resolved));
            }
            if (!paths.isEmpty()) {
                return paths;
            }
        }

        List<String> glbPaths = new ArrayList<>();
        if (!data.getMultiblocks().isEmpty()) {
            for (TFCMultiblockData block : data.getMultiblocks()) {
                String cacheKey = generateCacheKey(block.getPattern(), block.getMapping());
                if (GLB_CACHE.containsKey(cacheKey)) {
                    glbPaths.add(GLB_CACHE.get(cacheKey));
                    continue;
                }
                try {
                    Node node = multiblockSceneBuilder.buildMultiblock(block.getPattern(), block.getMapping());
                    String blockId = (block.getMultiblockId() != null
                            ? block.getMultiblockId().replaceAll("\\W+", "_")
                            : "block_") + cacheKey;
                    String glbPath = exportGlb(node, "assets/generated/" + blockId + ".glb");
                    GLB_CACHE.put(cacheKey, glbPath);
                    glbPaths.add(glbPath);
                } catch (Exception e) {
                    log.error("Failed to generate GLB for multiblock: {}, error: {}", block.getMultiblockId(), e.getMessage());
                }
            }
        } else {
            throw new RuntimeException("Multiblock : No TFC multiblocks found");
        }

        if (glbPaths.isEmpty()) {
            throw new RuntimeException("Multiblock : No GLB files could be generated");
        }
        return glbPaths;
    }

    public String generateMultiblockGLB(PageMultiblock data) throws Exception {
        if (multiblockResolver != null) {
            Optional<PageMultiblockData> resolved = multiblockResolver.resolve(data);
            if (resolved.isPresent()) {
                return generateMultiblockGLB(resolved.get());
            }
        }
        if (data.getMultiblock() != null) {
            return generateMultiblockGLB(data.getMultiblock());
        }
        throw new RuntimeException("Multiblock : Custom Multiblock '" + data.getMultiblockId() + "'");
    }

    public String generateMultiblockGLB(PageMultiblockData multiblock) throws Exception {
        String cacheKey = generateCacheKey(multiblock.getPattern(), multiblock.getMapping());
        if (GLB_CACHE.containsKey(cacheKey)) {
            return GLB_CACHE.get(cacheKey);
        }
        Node node = multiblockSceneBuilder.buildMultiblock(multiblock.getPattern(), multiblock.getMapping());
        String glbPath = exportGlb(node, "assets/generated/block_" + cacheKey + ".glb");
        GLB_CACHE.put(cacheKey, glbPath);
        return glbPath;
    }

    private String exportGlb(Node node, String glbPath) throws Exception {
        Path outputPath = loader.getOutputDir().resolve(glbPath);
        if (!Files.exists(outputPath)) {
            new GlTFExporter().export(node, outputPath.toString());
        }
        return glbPath;
    }

    private static String generateCacheKey(String[][] pattern, Map<String, String> mapping) {
        StringBuilder keyBuilder = new StringBuilder();
        for (String[] row : pattern) {
            keyBuilder.append(String.join(",", row)).append("|");
        }
        if (mapping != null) {
            mapping.keySet().stream()
                    .sorted()
                    .forEach(key -> keyBuilder.append(key).append(":").append(mapping.get(key)).append("|"));
        }
        return Integer.toHexString(keyBuilder.toString().hashCode());
    }

    private static BufferedImage resizeImage(BufferedImage original, int targetWidth, int targetHeight) {
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        return resized;
    }
}
