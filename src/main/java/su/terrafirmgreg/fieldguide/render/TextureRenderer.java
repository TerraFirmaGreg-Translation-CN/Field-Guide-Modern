package su.terrafirmgreg.fieldguide.render;

import com.madgag.gif.fmsware.AnimatedGifEncoder;
import su.terrafirmgreg.fieldguide.Pair;
import su.terrafirmgreg.fieldguide.asset.AssetKey;
import su.terrafirmgreg.fieldguide.asset.AssetLoader;
import su.terrafirmgreg.fieldguide.asset.FluidResult;
import su.terrafirmgreg.fieldguide.asset.ItemImageResult;
import su.terrafirmgreg.fieldguide.data.agedalcohol.AgedAlcohol;
import su.terrafirmgreg.fieldguide.data.minecraft.DyeColor;
import su.terrafirmgreg.fieldguide.data.minecraft.blockmodel.BlockModel;
import su.terrafirmgreg.fieldguide.data.minecraft.blockstate.BlockVariant;
import su.terrafirmgreg.fieldguide.data.patchouli.page.PageMultiblock;
import su.terrafirmgreg.fieldguide.data.patchouli.page.PageMultiblockData;
import su.terrafirmgreg.fieldguide.data.tfc.page.PageMultiMultiblock;
import su.terrafirmgreg.fieldguide.data.tfc.page.TFCMultiblockData;
import su.terrafirmgreg.fieldguide.exception.InternalException;
import su.terrafirmgreg.fieldguide.export.GlTFExporter;
import su.terrafirmgreg.fieldguide.localization.I18n;
import su.terrafirmgreg.fieldguide.localization.LocalizationManager;
import su.terrafirmgreg.fieldguide.render3d.scene.Node;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@Slf4j
public class TextureRenderer {
    
    // 透视变换的目标坐标（优化后的坐标）
    private static final Point[] LEFT_FACE_POINTS = {
        new Point(16, 231),   // 左下
        new Point(150, 298),  // 右下
        new Point(150, 135),  // 右上
        new Point(16, 68)     // 左上
    };

    private static final Point[] RIGHT_FACE_POINTS = {
        new Point(150, 298),  // 左下
        new Point(283, 231),  // 右下
        new Point(283, 68),   // 右上
        new Point(150, 135)   // 左上
    };

    private static final Point[] TOP_FACE_POINTS = {
        new Point(16, 68),    // 左下
        new Point(150, 136),  // 右下（连接点）
        new Point(284, 68),   // 右上
        new Point(150, 0)    // 左上
    };


    /// fluid images

    private final Map<String, ItemImageResult> FLUID_CACHE = new HashMap<>();

    // 流体颜色映射
    private final static Map<String, String> FLUID_COLORS = new HashMap<>();

    private final AssetLoader loader;
    private final LocalizationManager localizationManager;
    private final SingleBlock3DRenderer singleBlock3DRenderer;
    private final Multiblock3DRenderer multiblock3DRenderer;
    private final BlockStateModelBuilder blockStateModelBuilder;

    // Cache
    private final Map<String, String> IMAGE_CACHE = new HashMap<>();
    private final Map<String, ItemImageResult> itemImageCache = new HashMap<>();

    private final Set<String> missingImages = new TreeSet<>();

    private final Map<String, Integer> lastUid = new HashMap<>();

    public TextureRenderer(AssetLoader loader, LocalizationManager localizationManager) {
        this.loader = loader;
        this.localizationManager = localizationManager;
        this.blockStateModelBuilder = new BlockStateModelBuilder(loader);
        this.singleBlock3DRenderer = new SingleBlock3DRenderer(new BaseModelBuilder(loader), 256, 256);
        this.multiblock3DRenderer = new Multiblock3DRenderer(blockStateModelBuilder, 256, 256);

        lastUid.put("content", 0);
        lastUid.put("image", 0);
        lastUid.put("item", 0);
        lastUid.put("block", 0);
        lastUid.put("model", 0);
        lastUid.put("fluid", 0);

        FLUID_COLORS.put("brine", "#DCD3C9");
        FLUID_COLORS.put("curdled_milk", "#FFFBE8");
        FLUID_COLORS.put("limewater", "#B4B4B4");
        FLUID_COLORS.put("lye", "#feffde");
        FLUID_COLORS.put("milk_vinegar", "#FFFBE8");
        FLUID_COLORS.put("olive_oil", "#6A7537");
        FLUID_COLORS.put("olive_oil_water", "#4A4702");
        FLUID_COLORS.put("tannin", "#63594E");
        FLUID_COLORS.put("tallow", "#EDE9CF");
        FLUID_COLORS.put("vinegar", "#C7C2AA");
        FLUID_COLORS.put("beer", "#C39E37");
        FLUID_COLORS.put("cider", "#B0AE32");
        FLUID_COLORS.put("rum", "#6E0123");
        FLUID_COLORS.put("sake", "#B7D9BC");
        FLUID_COLORS.put("vodka", "#DCDCDC");
        FLUID_COLORS.put("whiskey", "#583719");
        FLUID_COLORS.put("corn_whiskey", "#D9C7B7");
        FLUID_COLORS.put("rye_whiskey", "#C77D51");
        FLUID_COLORS.put("water", "#2245CB");
        FLUID_COLORS.put("salt_water", "#4E63B9");
        FLUID_COLORS.put("spring_water", "#8AA3FF");
        //FLUID_COLORS.put("yak_milk", "#E8E8E8");// override by firmalife
        //FLUID_COLORS.put("goat_milk", "#E8E8E8");// override by firmalife
        // FLUID_COLORS.put("chocolate", "#756745");// override by firmalife

        // firmalife fluids
        FLUID_COLORS.put("yeast_starter", "#A79464");
        FLUID_COLORS.put("coconut_milk", "#FCFAE2");
        FLUID_COLORS.put("yak_milk", "#FCFAEC");
        FLUID_COLORS.put("goat_milk", "#F6F6EB");
        FLUID_COLORS.put("curdled_yak_milk", "#F9F4D6");
        FLUID_COLORS.put("curdled_goat_milk", "#EEEED9");
        FLUID_COLORS.put("cream", "#FCF6C7");
        FLUID_COLORS.put("pina_colada", "#E4C06A");
        FLUID_COLORS.put("chocolate", "#875633");
        FLUID_COLORS.put("sugar_water", "#99EEFF");
        FLUID_COLORS.put("fruity_fluid", "#FF6619");
        FLUID_COLORS.put("mead", "#C79F28");
        FLUID_COLORS.put("soybean_oil", "#DBC99A");

        for (DyeColor color : DyeColor.values()) {
            String name = color.name().toLowerCase(Locale.ROOT);
            FLUID_COLORS.put(name + "_dye", "#" + Integer.toHexString(color.getColor()));
        }
        for (AgedAlcohol alcohol : AgedAlcohol.values()) {
            FLUID_COLORS.put(alcohol.getId(), "#" + Integer.toHexString(alcohol.getColor()));
        }
    }

    public String nextId(String prefix) {
        int count = lastUid.getOrDefault(prefix, 0) + 1;
        lastUid.put(prefix, count);
        return prefix + count;
    }

    /**
     * Loads an item image, based on a specific keyed representation of an item.
     * The key may be an item ID ('foo:bar'), a tag ('#foo:bar'), or a csv list of item IDs ('foo:bar,foo:baz')
     * Using a global cache, the image will be generated, and saved to the _images/ directory.
     * For items that aren't render-able (for various reasons), this will use a placeholder image.
     * @param item
     * @param placeholder
     * @return
     *     path: The path to the item image (for use in href="", or src="")
     *     name: The translated name of the item (if a single item), or a best guess (if a tag), or None (if csv)
     */
    public ItemImageResult getItemImage(String item, boolean placeholder) {

        if (item.endsWith(".png")) {
            // This is not an item image, it must be a image directly
            return new ItemImageResult(convertIcon(item), null, null);
        }

        if (item.startsWith("tag:")) {
            item = "#" + item.substring(4);
            log.info("tag: {}", item);
        }

        // get from cache
        if (itemImageCache.containsKey(item)) {
            ItemImageResult result = itemImageCache.get(item);
            if (result.getKey() != null) {
                //Must re-translate the item each time, as the same image will be asked for in different localizations
                // TODO Translate, 这应该移到外面的渲染环节，这个类就纯粹处理图片。
                result.setName(localizationManager.translate("item." + result.getKey(), "block." + result.getKey()));
            }
            return result;
        }

        int nbtIndex = item.indexOf('{');
        if (nbtIndex > 0) {
            log.warn("Item with NBT: {}", item);
            item = item.substring(0, nbtIndex);// TODO remove it for test, maybe nbt support is much harder than I think
        }

        String name = null;
        String key = null;// translation key, if this needs to be re-translated
        java.util.List<String> items;

        boolean isItem = false;// this is to identify the itemId
        if (item.startsWith("#")) {
            name = localizationManager.translateWithArgs(I18n.TAG, item);
            items = loader.loadItemTag(item.substring(1));
        } else if (item.contains(",")) {
            items = Arrays.asList(item.split(","));
        } else {
            items = Collections.singletonList(item);
            isItem = true;
        }

        if (items.size() == 1) {
            // lazy load translation;
            int index = item.indexOf(':');
            if (index > 0) {
                String namespace = item.substring(0, index);
                if (namespace.startsWith("#")) {
                    namespace = namespace.substring(1);
                }
                localizationManager.lazyLoadNamespace(namespace);
            }
            key = items.getFirst().replace('/', '.').replace(':', '.');
            name = localizationManager.translate("item." + key, "block." + key);
        }

        try {
            // Create image for each item.
            List<BufferedImage> images = new ArrayList<>();
            for (String it : items) {
                try {
                    BufferedImage img = createItemImage(it);
                    if (img != null) {
                        images.add(img);
                    } else {
                        log.warn("image is null: {}", it);
                    }
                } catch (Exception e) {
                    // TODO add "e" later
                    log.error("Failed to create item image for {}, message: {}", it, e.getMessage(), e);
                }
            }

            if (images.isEmpty()) {
                log.error("Failed to create item image for: {}", item);
                // Fallback to using the placeholder image
                ItemImageResult fallback = new ItemImageResult("_images/placeholder_64.png", name, null);
                itemImageCache.put(item, fallback);
            }

            String path;
            String itemId = nextId("item");// counting
            if (images.size() == 1) {
            if (isItem) {
                AssetKey assetKey = new AssetKey(item, "textures", "assets", ".png");
                path = saveImage(assetKey.getResourcePath(), images.getFirst());
            } else {
                path = saveImage("assets/generated/" + itemId + ".png", images.getFirst());
            }
            } else {
                // # If any images are 64x64, then we need to resize them all to be 64x64 if we're saving a .gif
                boolean flag = false;
                for (BufferedImage image : images) {
                    if (image.getWidth() == 64 && image.getHeight() == 64) {
                        flag = true;
                        break;
                    }
                }
                if (flag) {
                    List<BufferedImage> resizedImages = new ArrayList<>();
                    for (BufferedImage image : images) {
                        if (image.getWidth() != 64 || image.getHeight() != 64) {
                            resizedImages.add(resizeImage(image, 64, 64));
                        } else {
                            resizedImages.add(image);
                        }
                    }
                    images = resizedImages;
                }

                path = saveGif("assets/generated/" + itemId + ".gif", images);
            }

            ItemImageResult result = new ItemImageResult(path, name, key);
            itemImageCache.put(item, result);
            return result;
        } catch (Exception e) {
            // TODO add e later
            log.error("Failed to create item image: {}, message: {}", item, e.getMessage());
            if (placeholder) {
                // Fallback to using the placeholder image
                ItemImageResult fallback = new ItemImageResult("_images/placeholder_64.png", name, null);
                itemImageCache.put(item, fallback);
                return fallback;
            }
            throw new InternalException("Failed to create item image: " + item);
        }
    }

    public BufferedImage createItemImage(String itemId) {

        BlockModel model = loader.loadItemModel(itemId);
        if (model.getParent() == null) {
            log.warn("Item model no parent: {}", itemId);
            // TODO 支持无parent的模型
            missingImages.add(itemId);
            throw new InternalException("Item model no parent: " + itemId);
        }
        // TODO

        if (model.getLoader() != null) {
            String loader = model.getLoader();
            if ("tfc:contained_fluid".equals(loader)) {
                String layer = model.getTextures().get("base");
                return this.loader.loadTexture(layer);
            } else if ("tfc:trim".equals(loader)) {
                return getTfcItemTrimLoader(model, itemId);
            } else if ("forge:separate_transforms".equals(loader)) {
                return getForgeSeparateTransformsLoader(model, itemId);
            } else if ("forge:fluid_container".equals(loader)) {
                return getForgeFluidContainerLoader(model, itemId);
            } else {
                log.error("Unknown loader: {} @ {}", loader, itemId);
            }
        }

        String parent = model.getParent();

        // FIXME 使用更通用的方式来判断模型是 item 还是 model
        int index = parent.indexOf(':');
        String namespace;
        String resId;
        if (index < 0) {
            namespace = "minecraft";
            resId = parent;
            parent = "minecraft:" + parent;
        } else {
            namespace = parent.substring(0, index);
            resId = parent.substring(index + 1);
        }

        int typeIndex = resId.indexOf('/');
        String type;
        if (typeIndex < 0) {
            type = "unknown";
        } else {
            type = resId.substring(0, typeIndex);
        }

        // FIXME 甚至干脆修改渲染方法，实现真正的继承层次 3D 渲染。
        if ("item".equals(type)) {
            try {
                // single-layer item model
                return defaultItemLoader(model, itemId);
            } catch (Exception e) {
                try {
                    BufferedImage img = createBlockModelImage(itemId, model);
                    img = resizeImage(img, 64, 64);
                    return img;
                } catch (Exception ex) {
                    // TODO add e later
                    missingImages.add(itemId);
                    log.error("Failed load model {} @ {}, message: {}", parent, itemId, e.getMessage());
                    throw new InternalException("Failed load item " + parent + " @ " + itemId);
                }
            }
        } else if ("block".equals(type)) {
            // Block model
            // TODO remove the try-catch
            try {
                BlockModel blockModel = loader.loadModel(parent);

                BufferedImage img = createBlockModelImage(itemId, blockModel);
                img = resizeImage(img, 64, 64);
                return img;
            } catch (Exception e) {
                // TODO add e later
                missingImages.add(itemId);
                log.error("Failed load model {} @ {}, message: {}", parent, itemId, e.getMessage());
                throw new InternalException("Failed load model " + parent + " @ " + itemId);
            }
        } else {
            missingImages.add(itemId);
            log.error("Unknown Parent {} @ {}, model: {}", parent, itemId, model);
            throw new InternalException("Unknown Parent " + parent + " @ " + itemId);
        }
    }

    private BufferedImage getForgeFluidContainerLoader(BlockModel model, String itemId) {
        String base = model.getTextures().get("base");
        String fluid = model.getTextures().get("fluid");
        String fluids = model.getFluid();

        String fluidColor;
        int index = fluids.indexOf(':');
        if (index > 0) {
            fluidColor = fluids.substring(index + 1);
        } else {
            fluidColor = fluids;
        }

        String color = FLUID_COLORS.get(fluidColor);
        if (color == null) {
            log.warn("Unknown fluid color: {} @ {}", fluidColor, itemId);
            missingImages.add(itemId);
            throw new InternalException("Unknown fluid color: " + fluidColor + " @ " + itemId);
        }
        BufferedImage fluidMaskImg = loader.loadTexture(fluid);
        BufferedImage colorImage = multiplyImageByColor(fluidMaskImg, parseColor(color));

        BufferedImage baseImage = loader.loadTexture(base);
        BufferedImage combined = new BufferedImage(baseImage.getWidth(), baseImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = combined.createGraphics();
        g.drawImage(baseImage, 0, 0, null);
        g.drawImage(colorImage, 0, 0, null);
        g.dispose();
        return combined;
    }

    private BufferedImage getForgeSeparateTransformsLoader(BlockModel model, String itemId) {
        BlockModel guiModel = model.getPerspectives().get("gui");
        BlockModel realModel = loader.loadModel(guiModel.getParent());
        return loader.loadTexture(realModel.getTextures().get("layer0"));
    }

    private BufferedImage getTfcItemTrimLoader(BlockModel model, String itemId) {
        String base = model.getTextures().get("armor");
        String overlay = model.getTextures().get("overlay");
        String trim = model.getTextures().get("trim");
        log.info("TFC Trim: {} {} {}", base, overlay, trim);
        if (base != null) {
            return overlayImage(base, overlay);
        } else {
            log.warn("Item model no base: {}", itemId);
            missingImages.add(itemId);
            throw new InternalException("Item model no base: " + itemId);
        }
    }

    private BufferedImage defaultItemLoader(BlockModel model, String itemId) {
        // single-layer item model
        String layer0 = model.getTextures().get("layer0");
        if (layer0 != null) {
            return loader.loadTexture(layer0);
        } else {
            log.warn("Item model no layer0: {}", itemId);
            missingImages.add(itemId);
            throw new InternalException("Item model no layer0: " + itemId);
        }
    }

    /**
     * 图片转换
     */
    public String convertImage(String image) {
        if (IMAGE_CACHE.containsKey(image)) {
            return IMAGE_CACHE.get(image);
        }

        try {
            AssetKey assetKey = loader.getTextureKey(image);
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

            nextId("image");// counting images
            String ref = saveImage(assetKey.getResourcePath(), cropped);
            IMAGE_CACHE.put(image, ref);
            return ref;
        } catch (Exception e) {
            throw new InternalException("Failed to convert image: " + image + " - " + e.getMessage());
        }
    }

    /**
     * 图标转换
     */
    public String convertIcon(String image) {
        if (IMAGE_CACHE.containsKey(image)) {
            return IMAGE_CACHE.get(image);
        }

        try {
            AssetKey assetKey = new AssetKey(image, null, "assets", ".png");
            BufferedImage img = loader.loadTexture(assetKey);
            int width = img.getWidth();
            int height = img.getHeight();

            if (width != 16 || height != 16) {
                log.warn("Icon must be 16x16: {} ({} x {})", image, width, height);
                throw new InternalException("Icon must be 16x16: " + image);
            }

            // 调整到64x64以匹配物品图标尺寸
            BufferedImage resized = resizeImage(img, 64, 64);
            String ref = saveImage(assetKey.getResourcePath(), resized);
            IMAGE_CACHE.put(image, ref);
            return ref;
        } catch (Exception e) {
            throw new InternalException("Failed to convert icon: " + image + " - " + e.getMessage());
        }
    }

    /**
     * Saves an image to a location based on an identifier.
     * @param path the resource path
     * @param image the image to save
     * @return the relative id to that location.
     */
    public String saveImage(String path, BufferedImage image) {
        File outputFile = loader.getOutputDir().resolve(path).toFile();
        try {
            // Create output directory if it doesn't exist
            FileUtils.createParentDirectories(outputFile);

            // Save the image
            ImageIO.write(image, "png", outputFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save image: " + outputFile.getAbsolutePath(), e);
        }

        // Return relative path
        return path;
    }

    /**
     * Saves multiple images to a .gif based on an identifier. Returns the relative path to that location.
     */
    public String saveGif(String path, List<BufferedImage> images) throws IOException {
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("Images list cannot be empty");
        }

        // Create output directory if it doesn't exist
        File outputFile = loader.getOutputDir().resolve(path).toFile();
        FileUtils.createParentDirectories(outputFile);

        // Save as GIF
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            AnimatedGifEncoder encoder = new AnimatedGifEncoder();
            encoder.start(fos);
            encoder.setDelay(1000);
            encoder.setRepeat(0);

            // 添加每一帧图像
            for (BufferedImage image : images) {
                encoder.addFrame(image);
            }
            encoder.finish();
        }

        return path;
    }

    /// ///////////// block_loader
    ///

    private static final Map<String, String> CACHE = new HashMap<>();
    private static final Map<String, String> GLB_CACHE = new HashMap<>();

    // 透视变换系数（与Python版本相同）
    private static final double[] LEFT = calculatePerspectiveCoefficients(
            new Point2D.Double(0, 0), new Point2D.Double(16, 0),
            new Point2D.Double(16, 16), new Point2D.Double(0, 16),
            new Point2D.Double(13, 57), new Point2D.Double(128, 114),
            new Point2D.Double(128, 255), new Point2D.Double(13, 198)
    );

    private static final double[] RIGHT = calculatePerspectiveCoefficients(
            new Point2D.Double(0, 0), new Point2D.Double(16, 0),
            new Point2D.Double(16, 16), new Point2D.Double(0, 16),
            new Point2D.Double(128, 114), new Point2D.Double(242, 58),
            new Point2D.Double(242, 197), new Point2D.Double(128, 255)
    );

    private static final double[] TOP = calculatePerspectiveCoefficients(
            new Point2D.Double(0, 0), new Point2D.Double(16, 0),
            new Point2D.Double(16, 16), new Point2D.Double(0, 16),
            new Point2D.Double(13, 57), new Point2D.Double(127, 0),
            new Point2D.Double(242, 58), new Point2D.Double(128, 114)
    );

    private static final double[] TOP_SLAB = calculatePerspectiveCoefficients(
            new Point2D.Double(0, 0), new Point2D.Double(16, 0),
            new Point2D.Double(16, 16), new Point2D.Double(0, 16),
            new Point2D.Double(13, 128), new Point2D.Double(127, 71),
            new Point2D.Double(242, 129), new Point2D.Double(128, 185)
    );

    // 计算透视变换系数
    public static double[] calculatePerspectiveCoefficients(Point2D.Double... points) {
        if (points.length != 8) {
            throw new IllegalArgumentException("需要8个点（4个源点，4个目标点）");
        }

        // 源点
        Point2D.Double[] src = {points[0], points[1], points[2], points[3]};
        // 目标点
        Point2D.Double[] dst = {points[4], points[5], points[6], points[7]};

        return findHomography(src, dst);
    }

    // 计算单应性矩阵（透视变换矩阵）
    public static double[] findHomography(Point2D.Double[] src, Point2D.Double[] dst) {
        // 构建矩阵A
        double[][] A = new double[8][9];

        for (int i = 0; i < 4; i++) {
            double x = src[i].x, y = src[i].y;
            double u = dst[i].x, v = dst[i].y;

            A[2*i][0] = x;
            A[2*i][1] = y;
            A[2*i][2] = 1;
            A[2*i][3] = 0;
            A[2*i][4] = 0;
            A[2*i][5] = 0;
            A[2*i][6] = -u * x;
            A[2*i][7] = -u * y;
            A[2*i][8] = -u;

            A[2*i+1][0] = 0;
            A[2*i+1][1] = 0;
            A[2*i+1][2] = 0;
            A[2*i+1][3] = x;
            A[2*i+1][4] = y;
            A[2*i+1][5] = 1;
            A[2*i+1][6] = -v * x;
            A[2*i+1][7] = -v * y;
            A[2*i+1][8] = -v;
        }

        // 使用SVD分解求解
        return solveWithSVD(A);
    }

    // 使用SVD分解求解线性方程组
    private static double[] solveWithSVD(double[][] A) {
        int m = A.length;
        int n = A[0].length;

        // 转换为单维数组进行SVD
        double[] a = new double[m * n];
        for (int i = 0; i < m; i++) {
            System.arraycopy(A[i], 0, a, i * n, n);
        }

        // 这里使用简化的SVD实现
        // 在实际应用中，您可能需要使用更健壮的SVD实现
        Jama.Matrix matA = new Jama.Matrix(A);
        Jama.SingularValueDecomposition svd = matA.svd();

        Jama.Matrix V = svd.getV();
        double[] h = new double[9];
        for (int i = 0; i < 9; i++) {
            h[i] = V.get(i, 8);
        }

        // 归一化
        double norm = h[8];
        for (int i = 0; i < 9; i++) {
            h[i] /= norm;
        }

        // 返回前8个系数（与PIL兼容）
        double[] coefficients = new double[8];
        System.arraycopy(h, 0, coefficients, 0, 8);
        return coefficients;
    }

    private static final Point[] ROOT = {
            new Point(0, 0), new Point(16, 0),
            new Point(16, 16), new Point(0, 16)
    };

    /**
     * 存储多模型图像和GLB路径的结果类
     */
    public static class MultiModelResult {
        private final String imagePath;
        private final List<String> glbPaths;

        public MultiModelResult(String imagePath, List<String> glbPaths) {
            this.imagePath = imagePath;
            this.glbPaths = glbPaths;
        }

        public String getImagePath() {
            return imagePath;
        }

        public List<String> getGlbPaths() {
            return glbPaths;
        }
    }

    /**
     * 为多个多方块生成GLB文件列表
     * @param data 多方块数据
     * @return GLB文件路径列表
     */
    public List<String> generateMultiMultiblockGLB(PageMultiMultiblock data) throws Exception {
        List<String> glbPaths = new ArrayList<>();

        // 检查是否有TFC多方块数据
        if (!data.getMultiblocks().isEmpty()) {
            List<TFCMultiblockData> multiblocks = data.getMultiblocks();
            
            // 为每个多方块生成GLB文件
            for (TFCMultiblockData block : multiblocks) {
                // 生成缓存键：基于多方块的模式和映射
                String cacheKey = generateCacheKey(block.getPattern(), block.getMapping());
                
                // 检查缓存
                if (GLB_CACHE.containsKey(cacheKey)) {
                    glbPaths.add(GLB_CACHE.get(cacheKey));
                    log.debug("Using cached GLB file for multiblock: {}", block.getMultiblockId());
                    continue;
                }
                
                try {
                    // 构建多方块节点
                    Node node = multiblock3DRenderer.buildMultiblock(block.getPattern(), block.getMapping());
                    
                    // 生成唯一的blockId
                    String blockId = (block.getMultiblockId() != null ? 
                        block.getMultiblockId().replaceAll("\\W+", "_") : "block_") + 
                        cacheKey;
                    
                    // 导出GLB文件
                    GlTFExporter exporter = new GlTFExporter();
                    String glbPath = "assets/generated/" + blockId + ".glb";
                    Path outputPath = loader.getOutputDir().resolve(glbPath);
                    
                    // 检查文件是否已存在
                    if (!Files.exists(outputPath)) {
                        exporter.export(node, outputPath.toString());
                        log.info("Generated GLB file for multiblock: {}", blockId);
                    } else {
                        log.debug("GLB file already exists: {}", blockId);
                    }
                    
                    // 缓存结果
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
            log.warn("Multiblock : No GLB files could be generated");
            throw new RuntimeException("Multiblock : No GLB files could be generated");
        }

        return glbPaths;
    }

    @Deprecated
    public MultiModelResult getMultiModelResult(PageMultiMultiblock data) throws Exception {
        List<BufferedImage> images = new ArrayList<>();
        List<String> glbPaths = new ArrayList<>();

        // 检查是否有TFC多方块数据
        if (!data.getMultiblocks().isEmpty()) {
            List<TFCMultiblockData> multiblocks = data.getMultiblocks();
            
            // 为每个多方块生成图像和GLB文件
            for (TFCMultiblockData block : multiblocks) {
                // 生成缓存键
                String cacheKey = generateCacheKey(block.getPattern(), block.getMapping());
                
                try {
                    // 构建多方块节点
                    Node node = multiblock3DRenderer.buildMultiblock(block.getPattern(), block.getMapping());
                    
                    // 渲染图像
                    BufferedImage image = multiblock3DRenderer.render(node);
                    images.add(image);
                    
                    // 检查GLB缓存
                    if (GLB_CACHE.containsKey(cacheKey)) {
                        glbPaths.add(GLB_CACHE.get(cacheKey));
                        log.debug("Using cached GLB file for multiblock: {}", block.getMultiblockId());
                    } else {
                        // 生成唯一的blockId
                        String blockId = (block.getMultiblockId() != null ? 
                            block.getMultiblockId().replaceAll("\\W+", "_") : "block_") + 
                            cacheKey;
                        
                        // 导出GLB文件
                        GlTFExporter exporter = new GlTFExporter();
                        String glbPath = "assets/generated/" + blockId + ".glb";
                        Path outputPath = loader.getOutputDir().resolve(glbPath);
                        
                        // 检查文件是否已存在
                        if (!Files.exists(outputPath)) {
                            exporter.export(node, outputPath.toString());
                            log.info("Generated GLB file for multiblock: {}", blockId);
                        }
                        
                        // 缓存结果
                        GLB_CACHE.put(cacheKey, glbPath);
                        glbPaths.add(glbPath);
                    }
                } catch (Exception e) {
                    log.error("Failed to process multiblock: {}, error: {}", block.getMultiblockId(), e.getMessage());
                    // 继续处理其他多方块，不中断整个流程
                }
            }
        } else {
            throw new RuntimeException("Multiblock : No TFC multiblocks found");
        }

        if (images.isEmpty()) {
            log.warn("Multiblock : No multiblocks could be rendered");
            throw new RuntimeException("Multiblock : No multiblocks could be rendered");
        }

        String imagePath;
        String baseId = nextId("multiblock_image");
        if (images.size() == 1) {
            // 单个图像直接保存为PNG
            imagePath = saveImage("assets/generated/" + baseId + ".png", images.getFirst());
        } else {
            // 多个图像也保存为PNG（第一帧），GLB序列用于动画展示
            imagePath = saveImage("assets/generated/" + baseId + ".png", images.getFirst());
        }

        // 返回结果，包含图像路径和GLB文件路径列表
        // 前端将使用glbPaths列表进行每秒切换显示
        return new MultiModelResult(imagePath, glbPaths);
    }

    /**
     * 兼容旧方法，仅返回图像路径
     * @param data 多方块数据
     * @return 图像路径
     */
    public String getMultiBlockImage(PageMultiMultiblock data) throws Exception {
        return getMultiModelResult(data).getImagePath();
    }

    /**
     * 为单个多方块生成GLB文件
     * @param data 多方块数据
     * @return GLB文件路径
     */
    public String generateMultiblockGLB(PageMultiblock data) throws Exception {
        if (data.getMultiblock() != null) {
            PageMultiblockData multiblock = data.getMultiblock();
            
            // 生成缓存键
            String cacheKey = generateCacheKey(multiblock.getPattern(), multiblock.getMapping());
            
            // 检查缓存
            if (GLB_CACHE.containsKey(cacheKey)) {
                log.debug("Using cached GLB file for multiblock");
                return GLB_CACHE.get(cacheKey);
            }
            
            // 构建多方块节点
            Node node = multiblock3DRenderer.buildMultiblock(multiblock.getPattern(), multiblock.getMapping());
            
            // 生成基于缓存键的blockId
            String blockId = "block_" + cacheKey;
            
            // 导出GLB文件
            GlTFExporter exporter = new GlTFExporter();
            String glbPath = "assets/generated/" + blockId + ".glb";
            Path outputPath = loader.getOutputDir().resolve(glbPath);
            
            // 检查文件是否已存在
            if (!Files.exists(outputPath)) {
                exporter.export(node, outputPath.toString());
                log.info("Generated GLB file for multiblock: {}", blockId);
            }
            
            // 缓存结果
            GLB_CACHE.put(cacheKey, glbPath);
            return glbPath;
        } else {
            throw new RuntimeException("Multiblock : Custom Multiblock '" + data.getMultiblockId() + "'");
        }
    }
    
    /**
     * 生成多方块缓存键
     * 基于模式和映射生成唯一标识符
     */
    private String generateCacheKey(String[][] pattern, Map<String, String> mapping) {
        StringBuilder keyBuilder = new StringBuilder();
        
        // 添加模式信息
        for (String[] row : pattern) {
            keyBuilder.append(String.join(",", row)).append("|");
        }
        
        // 添加映射信息（按键排序确保一致性）
        if (mapping != null) {
            mapping.keySet().stream()
                .sorted()
                .forEach(key -> keyBuilder.append(key).append(":").append(mapping.get(key)).append("|")
            );
        }
        
        // 使用哈希值作为最终键，避免键过长
        return Integer.toHexString(keyBuilder.toString().hashCode());
    }

    public String getMultiBlockImage(PageMultiblock data) throws Exception {
        String key;
        List<BufferedImage> images;

        Node node = null;
        if (data.getMultiblock() != null) {
            PageMultiblockData multiblock = data.getMultiblock();

            node = multiblock3DRenderer.buildMultiblock(multiblock.getPattern(), multiblock.getMapping());
            Pair<String, List<BufferedImage>> result = getMultiBlockImages(multiblock);
            key = result.getKey();
            images = result.getValue();
        } else {
            throw new RuntimeException("Multiblock : Custom Multiblock '" + data.getMultiblockId() + "'");
        }

        if (CACHE.containsKey(key)) {
            return CACHE.get(key);
        }

        String path;
        String blockId = nextId("block");// counting blocks
        if (images.size() == 1) {
            path = saveImage("assets/generated/" + blockId + ".png", images.getFirst());
        } else {
            path = saveGif("assets/generated/" + blockId + ".gif", images);
        }

        if (node != null) {
            // 生成缓存键
            PageMultiblockData multiblock = data.getMultiblock();
            String cacheKey = generateCacheKey(multiblock.getPattern(), multiblock.getMapping());
            
            // 检查缓存
            if (!GLB_CACHE.containsKey(cacheKey)) {
                GlTFExporter exporter = new GlTFExporter();
                String glbPath = "assets/generated/" + blockId + ".glb";
                Path outputPath = loader.getOutputDir().resolve(glbPath);
                
                // 检查文件是否已存在
                if (!Files.exists(outputPath)) {
                    exporter.export(node, outputPath.toString());
                }
                
                // 缓存结果
                GLB_CACHE.put(cacheKey, glbPath);
            }
        }

        CACHE.put(key, path);
        return path;
    }

    // FIXME 重构实现真正的多方快结构
    public Pair<String, List<BufferedImage>> getMultiBlockImages(PageMultiblockData data) throws Exception {
        if (data.getPattern() == null) {
            throw new RuntimeException("Multiblock : No 'pattern' field");
        }

        String[][] pattern = data.getPattern();
        String[][] validPattern1 = {{"X"}, {"0"}};
        String[][] validPattern2 = {{"X"}, {"Y"}, {"0"}};
        String[][] validPattern3 = {{"0"}, {" "}};

        boolean isTFGOre = false;
        if (!Arrays.deepEquals(pattern, validPattern1) && !Arrays.deepEquals(pattern, validPattern2)) {
            if (!Arrays.deepEquals(pattern, validPattern3)) {
                try {
                    Node node = multiblock3DRenderer.buildMultiblock(data.getPattern(), data.getMapping());
                    BufferedImage image = multiblock3DRenderer.render(node);
                    return new Pair<>(Arrays.deepToString(pattern), List.of(image));
                } catch (Exception e) {
                    log.error("Failed loading multiblock image: {}, mapping: {}, message: {}", Arrays.deepToString(pattern), data.getMapping(), e.getMessage(), e);
                }
                throw new RuntimeException("Multiblock : Complex Pattern '" + Arrays.deepToString(pattern) + "'");
            } else {
                isTFGOre = true;
            }
        }

        String block = data.getMapping().get(isTFGOre ? "0" : "X");
        List<String> blocks;

        if (block.startsWith("#")) {
            blocks = loader.loadBlockTag(block.substring(1));
        } else {
            blocks = List.of(block);
        }

        List<BufferedImage> blockImages = new ArrayList<>();
        for (String b : blocks) {
            try {
                BufferedImage image = getBlockImage(b);
                image = resizeImage(image, 64, 64);
                blockImages.add(image);
            } catch (Exception e) {
                log.error("Failed loading block image: " + b + ", message: " + e.getMessage(), e);
            }
        }

        return new Pair<>(block, blockImages);
    }

    public BufferedImage getBlockImage(String blockState) throws Exception {

        BlockVariant parsedState = AssetLoader.parseBlockState(blockState);
        String block = parsedState.getBlock();

        BlockModel model = loader.loadBlockModelWithState(blockState);
        return createBlockModelImage(block, model);
    }

    public static Pair<String, Map<String, String>> parseBlockState(String blockState) {
        if (blockState.contains("[")) {
            String[] parts = blockState.substring(0, blockState.length() - 1).split("\\[");
            String block = parts[0];
            Map<String, String> properties = parseBlockProperties(parts[1]);
            return new Pair<>(block, properties);
        } else {
            return new Pair<>(blockState, new HashMap<>());
        }
    }

    public static Map<String, String> parseBlockProperties(String properties) {
        Map<String, String> state = new HashMap<>();
        if (properties.contains("=")) {
            String[] pairs = properties.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                state.put(keyValue[0], keyValue[1]);
            }
        }
        return state;
    }

    public BufferedImage createBlockModelImage(String block, BlockModel model) {
        String parent = model.getParent();
        if (parent != null && parent.indexOf(':') < 0) {
            parent = "minecraft:" + parent;
        }
        Map<String, String> textures = model.getTextures();

        if (model.instanceOf("minecraft:block/cube_all")) {
            BufferedImage textureAll = loader.loadTexture(textures.get("all"));
            return createBlockModelProjection(textureAll, textureAll, textureAll, false);
        } else if (model.instanceOf("minecraft:block/cube_column")) {
            BufferedImage side = loader.loadTexture(textures.get("side"));
            BufferedImage end = loader.loadTexture(textures.get("end"));
            return createBlockModelProjection(side, side, end, false);
        } else if (model.instanceOf("minecraft:block/cube_column_horizontal")) {
            BufferedImage sideH = loader.loadTexture(textures.get("side"));
            BufferedImage endH = loader.loadTexture(textures.get("end"));
            return createBlockModelProjection(endH, sideH, sideH, true);
        }else if (model.instanceOf("minecraft:block/template_farmland")) {
            BufferedImage dirt = loader.loadTexture(textures.get("dirt"));
            BufferedImage top = loader.loadTexture(textures.get("top"));
            return createBlockModelProjection(dirt, dirt, top, false);
        } else if (model.instanceOf( "minecraft:block/slab")) {
            BufferedImage topSlab = loader.loadTexture(textures.get("top"));
            BufferedImage sideSlab = loader.loadTexture(textures.get("side"));
            return createSlabBlockModelProjection(sideSlab, sideSlab, topSlab);
        } else if (model.instanceOf("minecraft:block/crop")) {
            BufferedImage crop = loader.loadTexture(textures.get("crop"));
            return createCropModelProjection(crop);
        } else if (model.instanceOf("minecraft:block/leaves")) {
            BufferedImage textureAll = loader.loadTexture(textures.get("all"));
            return createBlockModelProjection(textureAll, textureAll, textureAll, false);
        } else if (model.instanceOf("tfc:block/thatch")) {
            BufferedImage textureAll = loader.loadTexture(textures.get("texture"));
            return createBlockModelProjection(textureAll, textureAll, textureAll, false);
        } else if (model.instanceOf("tfc:block/ore")) {
            BufferedImage combined = overlayImage(textures.get("all"), textures.get("overlay"));
            return createBlockModelProjection(combined, combined, combined, false);
        } else if (model.instanceOf("tfc:block/ore_column")) {
            BufferedImage combined = overlayImage(textures.get("side"), textures.get("overlay"));
            return createBlockModelProjection(combined, combined, combined, false);
        } else if (model.instanceOf("minecraft:air")) {
            BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(0, 0, 0, 0));
            g.fillRect(0, 0, 16, 16);
            return img;
        } else {
            try {
                return singleBlock3DRenderer.render(model);
            } catch (Exception e) {
                log.warn("Unsupported parent: {}@{}", parent, block);
                throw new RuntimeException("Block Model : Unknown Parent '" + parent + "' : at '" + block + "'");
            }
        }
    }

    public BufferedImage overlayImage(String baseId, String overlayId) {
        BufferedImage baseImage = loader.loadTexture(baseId);
        BufferedImage combined = new BufferedImage(baseImage.getWidth(), baseImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = combined.createGraphics();
        g.drawImage(baseImage, 0, 0, null);

        if (overlayId != null) {
            BufferedImage overlay = loader.loadTexture(overlayId);
            g.drawImage(overlay, 0, 0, null);
        }

        g.dispose();
        return combined;
    }

    public BufferedImage createBlockModelProjection(BufferedImage left, BufferedImage right, BufferedImage top, boolean rotate) {
        if (rotate) {
            right = rotateImage(right, 90);
            top = rotateImage(top, 90);
        }

        return TextureRenderer.createBlockImage(left, right, top);
    }

    public static BufferedImage createSlabBlockModelProjection(BufferedImage left, BufferedImage right, BufferedImage top) {
        // crop
        left = cropRetainingPosition(left, 0, 8, 16, 16);
        right = cropRetainingPosition(right, 0, 8, 16, 16);

        // 合并图像
        return TextureRenderer.createBlockImage(left, right, top);
    }

    public static BufferedImage createCropModelProjection(BufferedImage crop) {
        BufferedImage left = adjustBrightness(crop, 0.85f);
        BufferedImage right = adjustBrightness(crop, 0.6f);

        BufferedImage rEnd = cropRetainingPosition(right, 0, 0, 5, 16);
        BufferedImage lEnd = cropRetainingPosition(left, 13, 0, 16, 16);

        // 透视变换
        BufferedImage leftTransformed = perspectiveTransform(left, LEFT);
        BufferedImage rightTransformed = perspectiveTransform(right, RIGHT);
        BufferedImage rEndTransformed = perspectiveTransform(rEnd, RIGHT);
        BufferedImage lEndTransformed = perspectiveTransform(lEnd, LEFT);

        // 创建基础图像并粘贴各个部分
        BufferedImage base = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = base.createGraphics();

        g.drawImage(leftTransformed, 98 - 12, 14 - 56, null);
        g.drawImage(rightTransformed, 100 - 128, 100 - 114, null);
        g.drawImage(rightTransformed, 42 - 128, 72 - 114, null);
        g.drawImage(leftTransformed, 42 - 12, 42 - 56, null);
        g.drawImage(rEndTransformed, 100 - 128, 100 - 114, null);
        g.drawImage(rEndTransformed, 42 - 128, 72 - 114, null);
        g.drawImage(lEndTransformed, 98 - 12, 14 - 56, null);
        g.drawImage(lEndTransformed, 42 - 12, 42 - 56, null);

        g.dispose();
        return base;
    }

    public static BufferedImage cropRetainingPosition(BufferedImage img, int u1, int v1, int u2, int v2) {
        BufferedImage cropped = img.getSubimage(u1, v1, u2 - u1, v2 - v1);
        BufferedImage result = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.drawImage(cropped, u1, v1, null);
        g.dispose();
        return result;
    }

    public static BufferedImage rotateImage(BufferedImage image, double degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));

        int newWidth = (int) Math.round(image.getWidth() * cos + image.getHeight() * sin);
        int newHeight = (int) Math.round(image.getWidth() * sin + image.getHeight() * cos);

        BufferedImage result = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();

        AffineTransform transform = new AffineTransform();
        transform.translate(newWidth / 2.0, newHeight / 2.0);
        transform.rotate(radians);
        transform.translate(-image.getWidth() / 2.0, -image.getHeight() / 2.0);

        g.setTransform(transform);
        g.drawImage(image, 0, 0, null);
        g.dispose();

        return result;
    }

    public static BufferedImage perspectiveTransform(BufferedImage src, double[] coefficients) {
        int width = 256;
        int height = 256;
        BufferedImage dst = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // 逆变换：从目标像素找源像素
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Point2D.Double srcPoint = applyPerspectiveTransform(x, y, coefficients, true);

                if (srcPoint.x >= 0 && srcPoint.x < src.getWidth() &&
                        srcPoint.y >= 0 && srcPoint.y < src.getHeight()) {
                    int rgb = getBilinearInterpolation(src, srcPoint.x, srcPoint.y);
                    dst.setRGB(x, y, rgb);
                }
            }
        }

        return dst;
    }

    // 应用透视变换
    private static Point2D.Double applyPerspectiveTransform(double x, double y, double[] coeffs, boolean inverse) {
        if (inverse) {
            // 计算逆变换：从目标坐标找源坐标
            double denominator = coeffs[6] * x + coeffs[7] * y + 1;
            double srcX = (coeffs[0] * x + coeffs[1] * y + coeffs[2]) / denominator;
            double srcY = (coeffs[3] * x + coeffs[4] * y + coeffs[5]) / denominator;
            return new Point2D.Double(srcX, srcY);
        } else {
            // 正变换：从源坐标找目标坐标
            double denominator = coeffs[6] * x + coeffs[7] * y + 1;
            double dstX = (coeffs[0] * x + coeffs[1] * y + coeffs[2]) / denominator;
            double dstY = (coeffs[3] * x + coeffs[4] * y + coeffs[5]) / denominator;
            return new Point2D.Double(dstX, dstY);
        }
    }

    // 双线性插值
    private static int getBilinearInterpolation(BufferedImage img, double x, double y) {
        int x1 = (int) Math.floor(x);
        int y1 = (int) Math.floor(y);
        int x2 = x1 + 1;
        int y2 = y1 + 1;

        if (x2 >= img.getWidth()) x2 = img.getWidth() - 1;
        if (y2 >= img.getHeight()) y2 = img.getHeight() - 1;

        double dx = x - x1;
        double dy = y - y1;

        int q11 = img.getRGB(x1, y1);
        int q21 = img.getRGB(x2, y1);
        int q12 = img.getRGB(x1, y2);
        int q22 = img.getRGB(x2, y2);

        return bilinearInterpolate(q11, q21, q12, q22, dx, dy);
    }

    private static int bilinearInterpolate(int q11, int q21, int q12, int q22, double dx, double dy) {
        Color c11 = new Color(q11, true);
        Color c21 = new Color(q21, true);
        Color c12 = new Color(q12, true);
        Color c22 = new Color(q22, true);

        int r = (int) ((1 - dx) * (1 - dy) * c11.getRed() + dx * (1 - dy) * c21.getRed() +
                (1 - dx) * dy * c12.getRed() + dx * dy * c22.getRed());
        int g = (int) ((1 - dx) * (1 - dy) * c11.getGreen() + dx * (1 - dy) * c21.getGreen() +
                (1 - dx) * dy * c12.getGreen() + dx * dy * c22.getGreen());
        int b = (int) ((1 - dx) * (1 - dy) * c11.getBlue() + dx * (1 - dy) * c21.getBlue() +
                (1 - dx) * dy * c12.getBlue() + dx * dy * c22.getBlue());
        int a = (int) ((1 - dx) * (1 - dy) * c11.getAlpha() + dx * (1 - dy) * c21.getAlpha() +
                (1 - dx) * dy * c12.getAlpha() + dx * dy * c22.getAlpha());

        r = Math.min(255, Math.max(0, r));
        g = Math.min(255, Math.max(0, g));
        b = Math.min(255, Math.max(0, b));
        a = Math.min(255, Math.max(0, a));

        return new Color(r, g, b, a).getRGB();
    }

    public static BufferedImage createBlockImage(BufferedImage leftTexture,
                                               BufferedImage rightTexture, 
                                               BufferedImage topTexture) {
        BufferedImage result = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = result.createGraphics();
        
        // 设置高质量渲染
        setupHighQualityRendering(g2d);
        
        // 透明背景
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, 300, 300);
        g2d.setComposite(AlphaComposite.SrcOver);
        
        // 使用更高效的亮度调整
        BufferedImage darkLeft = adjustBrightness(leftTexture, 0.85f);
        BufferedImage darkRight = adjustBrightness(rightTexture, 0.6f);
        
        // 使用改进的纹理映射
        drawTexturedPolygon(g2d, darkLeft, LEFT_FACE_POINTS);
        drawTexturedPolygon(g2d, darkRight, RIGHT_FACE_POINTS);
        drawTexturedPolygon(g2d, topTexture, TOP_FACE_POINTS);
        
        g2d.dispose();
        return result;
    }
    
    private static void setupHighQualityRendering(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    }
    
    /**
     * 改进的纹理映射方法，使用透视变换
     */
    private static void drawTexturedPolygon(Graphics2D g2d, BufferedImage texture, Point[] points) {
        // 创建多边形路径
        Path2D polygon = new Path2D.Double();
        polygon.moveTo(points[0].x, points[0].y);
        for (int i = 1; i < points.length; i++) {
            polygon.lineTo(points[i].x, points[i].y);
        }
        polygon.closePath();
        
        // 保存原始裁剪区域
        Shape oldClip = g2d.getClip();
        g2d.setClip(polygon);
        
        try {
            // 计算多边形的边界
            Rectangle bounds = polygon.getBounds();
            
            // 对于平行四边形面，使用仿射变换
            if (isParallelogram(points)) {
                drawParallelogramTexture(g2d, texture, points);
            } else {
                // 对于梯形面，使用更复杂的变换
                drawTrapezoidTexture(g2d, texture, bounds);
            }
        } finally {
            // 恢复裁剪区域
            g2d.setClip(oldClip);
        }
    }
    
    /**
     * 检查是否为平行四边形
     */
    private static boolean isParallelogram(Point[] points) {
        if (points.length != 4) return false;
        
        // 检查对边是否平行
        double dx1 = points[1].x - points[0].x;
        double dy1 = points[1].y - points[0].y;
        double dx2 = points[2].x - points[3].x;
        double dy2 = points[2].y - points[3].y;
        
        // 简单的平行检查（向量叉积接近0）
        double cross1 = dx1 * dy2 - dy1 * dx2;
        return Math.abs(cross1) < 1.0;
    }
    
    /**
     * 绘制平行四边形纹理
     */
    private static void drawParallelogramTexture(Graphics2D g2d, BufferedImage texture, Point[] points) {
        // 计算仿射变换矩阵
        double sx1 = points[1].x - points[0].x;
        double sy1 = points[1].y - points[0].y;
        double sx2 = points[3].x - points[0].x;
        double sy2 = points[3].y - points[0].y;
        
        AffineTransform transform = new AffineTransform(
            sx1 / texture.getWidth(), sy1 / texture.getHeight(),
            sx2 / texture.getWidth(), sy2 / texture.getHeight(),
            points[0].x, points[0].y
        );
        
        g2d.drawImage(texture, transform, null);
    }
    
    /**
     * 绘制梯形纹理（简化版本）
     */
    private static void drawTrapezoidTexture(Graphics2D g2d, BufferedImage texture, Rectangle bounds) {
        // 对于梯形，使用缩放和平移（这是一个简化方案）
        // 在实际应用中，您可能需要真正的透视变换
        
        double scaleX = (double) bounds.width / texture.getWidth();
        double scaleY = (double) bounds.height / texture.getHeight();
        
        AffineTransform transform = new AffineTransform();
        transform.translate(bounds.x, bounds.y);
        transform.scale(scaleX, scaleY);
        
        g2d.drawImage(texture, transform, null);
    }

    public static BufferedImage adjustBrightness(BufferedImage image, float factor) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);
        
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            int a = (argb >> 24) & 0xFF;
            int r = (int) (((argb >> 16) & 0xFF) * factor);
            int g = (int) (((argb >> 8) & 0xFF) * factor);
            int b = (int) ((argb & 0xFF) * factor);
            
            r = Math.min(255, Math.max(0, r));
            g = Math.min(255, Math.max(0, g));
            b = Math.min(255, Math.max(0, b));
            
            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        
        result.setRGB(0, 0, width, height, pixels, 0, width);
        return result;
    }

    /**
     * 图片缩放工具方法
     */
    public static BufferedImage resizeImage(BufferedImage original, int targetWidth, int targetHeight) {
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resized.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

        g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        return resized;
    }

    public static BufferedImage multiplyImageByColor(BufferedImage grayscaleImage, Color color) {
        int width = grayscaleImage.getWidth();
        int height = grayscaleImage.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        float targetR = color.getRed() / 255.0f;
        float targetG = color.getGreen() / 255.0f;
        float targetB = color.getBlue() / 255.0f;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = grayscaleImage.getRGB(x, y);
                int alpha = (pixel >> 24) & 0xFF;
                int gray = (pixel >> 16) & 0xFF;
                float grayScale = gray / 255.0f;

                int r = (int) (grayScale * targetR * 255);
                int g = (int) (grayScale * targetG * 255);
                int b = (int) (grayScale * targetB * 255);

                r = Math.min(255, Math.max(0, r));
                g = Math.min(255, Math.max(0, g));
                b = Math.min(255, Math.max(0, b));

                int newPixel = (alpha << 24) | (r << 16) | (g << 8) | b;
                result.setRGB(x, y, newPixel);
            }
        }

        return result;
    }

    /// fluid texture

    /**
     * 获取流体图像
     */
    public ItemImageResult getFluidImage(Object fluidIn, boolean placeholder) {
        return getFluidImage(fluidIn, placeholder, true);
    }

    public ItemImageResult getFluidImage(Object fluidIn, boolean placeholder, boolean includeAmount) {
        FluidResult decoded = decodeFluid(fluidIn);
        String fluid = decoded.getFluid();
        int amount = decoded.getAmount();

        if (FLUID_CACHE.containsKey(fluid)) {
            ItemImageResult entry = FLUID_CACHE.get(fluid);
            String name = entry.getName();
            if (entry.getKey() != null) {
                try {
                    // 必须每次都重新翻译，因为相同的图像会在不同的本地化环境中被请求
                    name = localizationManager.translate("fluid." + entry.getKey(), "block." + entry.getKey());
                } catch (Exception e) {
                    System.err.println("Warning: " + e.getMessage());
                }
            }
            String finalName = includeAmount && amount > 0 ?
                    String.format("%s mB %s", amount, name) : name;
            entry.setName(finalName);
            return entry;
        }

        String name = null;
        String key = null;
        List<String> fluids;

        if (fluid.startsWith("#")) {
            name = String.format(localizationManager.translate(I18n.TAG), fluid);
            fluids = loader.loadFluidTag(fluid.substring(1));
        } else if (fluid.contains(",")) {
            fluids = Arrays.asList(fluid.split(","));
        } else {
            fluids = Collections.singletonList(fluid);
        }

        if (fluids.size() == 1) {
            key = fluids.get(0).replace("/", ".").replace(":", ".");
            try {
                name = localizationManager.translate("fluid." + key, "block." + key);
            } catch (Exception e) {
                System.err.println("Warning: " + e.getMessage());
            }
        }

        String path;
        try {
            List<BufferedImage> images = new ArrayList<>();
            for (String fluidId : fluids) {
                images.add(createFluidImage(fluidId));
            }

            String fluidId = nextId("fluid");// counting fluid
            if (images.size() == 1) {
                path = saveImage("assets/generated/" + fluidId + ".png", images.getFirst());
            } else {
                path = saveGif("assets/generated/" + fluidId + ".gif", images);
            }
        } catch (Exception e) {
            System.err.println("Warning: Fluid Image(s) - " + e.getMessage());

            if (placeholder) {
                // 回退到使用占位符图像
                path = "_images/fluid.png";
            } else {
                throw new RuntimeException(e);
            }
        }


        String finalName = includeAmount && amount > 0 ?
                String.format("%s mB %s", amount, name) : name;

        ItemImageResult result = new ItemImageResult(path, finalName, key);

        FLUID_CACHE.put(fluid, result);
        return result;
    }

    /**
     * 解码流体数据
     */
    public static FluidResult decodeFluid(Object item) {
        int amount = 0;
        String ingredient = null;

        if (item instanceof Map) {
            Map<?, ?> itemMap = (Map<?, ?>) item;
            if (itemMap.containsKey("ingredient")) {
                ingredient = decodeFluidIngredient(itemMap.get("ingredient"));
            } else if (itemMap.containsKey("fluid") || itemMap.containsKey("tag")) {
                ingredient = decodeFluidIngredient(item);
            }
            amount = itemMap.containsKey("amount") ? ((Number) itemMap.get("amount")).intValue() : 1000;
        } else if (item instanceof String) {
            ingredient = (String) item;
        }

        if (ingredient == null) {
            throw new RuntimeException("Invalid format for a fluid: '" + item + "'");
        } else {
            return new FluidResult(ingredient, amount);
        }
    }

    /**
     * 解码流体成分
     */
    @SuppressWarnings("unchecked")
    public static String decodeFluidIngredient(Object item) {
        if (item instanceof String) {
            return (String) item;
        } else if (item instanceof Map) {
            Map<String, Object> itemMap = (Map<String, Object>) item;
            if (itemMap.containsKey("fluid")) {
                return (String) itemMap.get("fluid");
            } else if (itemMap.containsKey("tag")) {
                return "#" + itemMap.get("tag");
            }
        }
        throw new RuntimeException("Could not decode fluid ingredient: " + item);
    }

    /**
     * 创建流体图像
     */
    public static BufferedImage createFluidImage(String fluid) {
        String path = fluid;
        if (path.contains(":")) {
            path = path.split(":", 2)[1];
        }

        // 加载基础流体图像并调整大小
        BufferedImage base;
        try {
            base = ImageIO.read(new File("assets/textures/fluid.png"));
        } catch (IOException e) {
            log.error("Load fluid texture failed", e);
            throw new InternalException("load fluid png failed");
        }
        base = resizeImage(base, 64, 64);

        if (!FLUID_COLORS.containsKey(path)) {
            System.out.println("Fluid " + path + " has no color specified.");
            return base;
        } else {
            Color color = parseColor(FLUID_COLORS.get(path));
            return applyColorToImage(base, color);
        }
    }

    /**
     * 将颜色应用到图像的所有像素上
     */
    public static BufferedImage applyColorToImage(BufferedImage img, Color color) {
        return applyColorToImage(img, color, 0.5f);
    }

    public static BufferedImage applyColorToImage(BufferedImage img, Color color, float darkThreshold) {
        BufferedImage result = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);

        float[] hsv = rgbToHsv(color.getRed(), color.getGreen(), color.getBlue());
        float hue = hsv[0];
        float saturation = hsv[1];
        float value = hsv[2];

        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                int rgb = img.getRGB(x, y);
                Color pixelColor = new Color(rgb, true);

                float[] pixelHsv = rgbToHsv(pixelColor.getRed(), pixelColor.getGreen(), pixelColor.getBlue());
                float newValue = value > darkThreshold ? pixelHsv[2] : pixelHsv[2] * 0.5f;

                Color newColor = hsvToRgb(hue, saturation, newValue, pixelColor.getAlpha());
                result.setRGB(x, y, newColor.getRGB());
            }
        }
        return result;
    }

    private static Color parseColor(String hex) {
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        return new Color(
                Integer.valueOf(hex.substring(0, 2), 16),
                Integer.valueOf(hex.substring(2, 4), 16),
                Integer.valueOf(hex.substring(4, 6), 16)
        );
    }

    private static float[] rgbToHsv(int r, int g, int b) {
        float[] hsv = new float[3];
        Color.RGBtoHSB(r, g, b, hsv);
        return hsv;
    }

    private static Color hsvToRgb(float h, float s, float v, int alpha) {
        int rgb = Color.HSBtoRGB(h, s, v);
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
    }
}