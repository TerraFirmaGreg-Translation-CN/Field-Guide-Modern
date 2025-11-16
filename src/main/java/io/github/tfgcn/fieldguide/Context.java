package io.github.tfgcn.fieldguide;

import com.google.gson.reflect.TypeToken;
import com.madgag.gif.fmsware.AnimatedGifEncoder;
import io.github.tfgcn.fieldguide.asset.Asset;
import io.github.tfgcn.fieldguide.asset.AssetKey;
import io.github.tfgcn.fieldguide.asset.AssetLoader;
import io.github.tfgcn.fieldguide.exception.InternalException;
import io.github.tfgcn.fieldguide.patchouli.BookCategory;
import io.github.tfgcn.fieldguide.patchouli.BookEntry;
import io.github.tfgcn.fieldguide.patchouli.page.IPageDoubleRecipe;
import io.github.tfgcn.fieldguide.patchouli.page.IPageWithText;
import io.github.tfgcn.fieldguide.asset.ItemImageResult;
import io.github.tfgcn.fieldguide.minecraft.BlockModel;
import io.github.tfgcn.fieldguide.patchouli.page.PageMultiblock;
import io.github.tfgcn.fieldguide.patchouli.page.PageMultiblockData;
import io.github.tfgcn.fieldguide.patchouli.page.tfc.PageMultiMultiblock;
import io.github.tfgcn.fieldguide.patchouli.page.tfc.TFCMultiblockData;
import io.github.tfgcn.fieldguide.renderer.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

import static io.github.tfgcn.fieldguide.renderer.TextureRenderer.adjustBrightness;
import static io.github.tfgcn.fieldguide.renderer.TextureRenderer.resizeImage;

@Slf4j
@Data
public class Context {
    // 静态缓存，所有 Context 实例共享
    private static final Map<String, String> IMAGE_CACHE = new HashMap<>();

    String[] NAMESPACES = {"tfc", "minecraft", "forge", "tfg", "beneath", "afc", "firmalife", "create", "gtceu", "createdeco", "rnr", "ae2", "waterflasks", "sns", "firmaciv", "alekiships", "greate", "sophisticatedbackpacks", "tfcagedalcohol", "tfcbetterbf", "tfcchannelcasting", "tfchotornot"};

    // 实例字段
    private final AssetLoader loader;
    private final HtmlRenderer htmlRenderer;
    private final Block3DRenderer block3DRenderer;
    private final Multiblock3DRenderer multiblock3DRenderer;
    private final String outputRootDir;// The output directory
    private final String rootDir;// The root directory to fetch static assets from
    private final boolean debugI18n;
    
    private String outputDir;
    private String lang;

    // 数据结构
    private Map<String, BookCategory> categoryMap = new HashMap<>();
    private Map<String, BookEntry> entryMap = new HashMap<>();
    private List<BookCategory> categories = new ArrayList<>();

    // 所有权管理
    private Map<String, String> categoryOwners = new HashMap<>();

    // language
    private Map<String, String> langFallbackKeys = new HashMap<>();// en_us
    private Map<String, String> langKeys = new HashMap<>();

    private Map<String, String> keybindings = new HashMap<>();

    // ID 计数器
    private Map<String, Integer> lastUid = new HashMap<>();

    public Set<String> missingKeys = new TreeSet<>();// FIXME remove later
    public Set<String> missingImages = new TreeSet<>();

    // 统计信息
    private int recipesFailed = 0;
    private int recipesPassed = 0;
    private int recipesSkipped = 0;
    private int itemsPassed = 0;
    private int itemsFailed = 0;
    private int blocksPassed = 0;
    private int blocksFailed = 0;
    
    // 搜索树
    private List<Map<String, String>> searchTree = new ArrayList<>();

    private Map<String, ItemImageResult> itemImageCache = new HashMap<>();

    public Context(AssetLoader loader, String outputRootDir, String rootDir, boolean debugI18n) throws IOException {
        this.loader = loader;
        this.outputRootDir = outputRootDir;
        this.outputDir = outputRootDir;
        this.rootDir = rootDir;
        this.debugI18n = debugI18n;

        this.htmlRenderer = new HtmlRenderer("assets/templates");
        this.block3DRenderer = new Block3DRenderer(loader, 256, 256);
        this.multiblock3DRenderer = new Multiblock3DRenderer(loader, 256, 256);

        // init en_us lang
        this.langFallbackKeys.putAll(loadLang("en_us"));

        // 初始化计数器
        lastUid.put("content", 0);
        lastUid.put("image", 0);
        lastUid.put("item", 0);
        lastUid.put("block", 0);
        lastUid.put("fluid", 0);
    }
    
    /**
     * 切换到指定语言
     */
    public Context withLang(String lang) {
        this.lang = lang;
        this.outputDir = ProjectUtil.pathJoin(outputRootDir, lang);
        
        this.categoryMap = new HashMap<>();
        this.entryMap = new HashMap<>();
        this.categories = new ArrayList<>();
        this.langKeys = new HashMap<>();
        
        loadTranslations(lang);
        
        this.keybindings = new HashMap<>();
        for (String key : I18n.KEYS) {
            String bindingKey = key.substring("field_guide.".length());
            this.keybindings.put(bindingKey, translate(key));
        }
        
        return this;
    }

    public String getSourcePath(String path) {
        // if in resource pack, first folder is "data", otherwise "assets"
        // for now only support "assets"
        return String.format("%s/tfc/patchouli_books/field_guide/%s/%s", "assets", getLang(), path);
    }

    public List<Asset> listAssets(String resourcePath) throws IOException {
        return loader.listAssets(resourcePath);
    }

    private Map<String, String> loadLang(String lang) {
        Map<String, String> langMap = new HashMap<>();
        // load mod lang
        for (String namespace : NAMESPACES) {
            Map<String, String> map = loader.loadLang(namespace, lang);
            log.info("Loaded {} Lang: {}", namespace, map.size());
            langMap.putAll(map);
        }
        return langMap;
    }

    /**
     * 加载翻译文件
     */
    private void loadTranslations(String lang) {
        langKeys.putAll(langFallbackKeys);

        loadLocalLang("en_us");

        if (!"en_us".equals(lang)) {
            Map<String, String> translations = loadLang(lang);
            langKeys.putAll(translations);

            loadLocalLang(lang);
        }
    }
    
    /**
     * 加载本地语言文件
     */
    private void loadLocalLang(String lang) {
        try {
            File langFile = new File(ProjectUtil.pathJoin("assets/lang", lang + ".json"));
            if (langFile.exists()) {
                String content = FileUtils.readFileToString(langFile, StandardCharsets.UTF_8);
                Type mapType = new TypeToken<Map<String, String>>() {}.getType();
                Map<String, String> data = JsonUtils.fromJson(content, mapType);
                
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    this.langKeys.put("field_guide." + entry.getKey(), entry.getValue());
                }
            }
        } catch (IOException e) {
            log.warn("Local Translation: {} : {}", lang, e.getMessage());
        }
    }
    
    /**
     * 生成唯一ID
     */
    public String nextId(String prefix) {
        int count = lastUid.getOrDefault(prefix, 0) + 1;
        lastUid.put(prefix, count);
        return prefix + count;
    }
    
    public String nextId() {
        return nextId("content");
    }

    public boolean hasCategory(String categoryId) {
        return this.categoryMap.containsKey(categoryId);
    }

    public void addCategory(BookCategory category) {
        this.categoryMap.put(category.getId(), category);
        this.categories.add(category);
    }

    public boolean hasEntry(String entryId) {
        return this.entryMap.containsKey(entryId);
    }

    public void addEntry(String categoryId, String entryId, BookEntry entry, Map<String, String> search) {
        try {
            this.entryMap.put(entryId, entry);
            this.categoryMap.get(categoryId).addEntry(entry);
            this.searchTree.add(search);
        } catch (Exception e) {
            // FIXME category not found for other languages
            log.warn("Add Entry: {} : {}, {}", categoryId, entryId, e.getMessage());
        }
    }
    
    /**
     * 对分类和条目进行排序
     */
    public void sort() {
        this.categories.sort(BookCategory::compareTo);
        for (BookCategory cat : this.categories) {
            cat.getEntries().sort(BookEntry::compareTo);
        }
    }
    
    public void formatText(List<String> buffer, String text, Map<String, String> search) {
        if (text != null && !text.isEmpty()) {
            TextFormatter.formatText(buffer, text, this.keybindings);

            if (search != null) {
                Map<String, String> searchData = new HashMap<>(search);
                searchData.put("content", text);
                this.searchTree.add(searchData);
            }
        }
    }

    public void formatText(List<String> buffer, IPageWithText page) {
        formatText(buffer, page.getText(), null);
    }
    
    public void formatTitle(List<String> buffer, String title, Map<String, String> search) {
        if (title != null && !title.isEmpty()) {
            String stripped = TextFormatter.stripVanillaFormatting(title);
            buffer.add("<h5>" + stripped + "</h5>\n");

            if (search != null) {
                Map<String, String> searchData = new HashMap<>(search);
                searchData.put("content", stripped);
                this.searchTree.add(searchData);
            }
        }
    }

    /**
     * 带图标的标题格式化
     */
    public void formatTitleWithIcon(List<String> buffer, String iconSrc, String iconName, 
                                   String inTitle, String tag,
                                   String tooltip, Map<String, String> search) {
        String title = iconName;
        if (inTitle != null && !inTitle.isEmpty()) {
            title = TextFormatter.stripVanillaFormatting(inTitle);
            if (iconName == null || iconName.isEmpty()) {
                iconName = title;
            }
            if (search != null) {
                Map<String, String> searchData = new HashMap<>(search);
                searchData.put("content", iconName);
                this.searchTree.add(searchData);
            }
        }
        
        if (tooltip == null) {
            tooltip = title;
        }
        
        String html = String.format("""
            <div class="item-header">
                <span href="#" data-bs-toggle="tooltip" title="%s">
                    <img src="%s" alt="%s" />
                </span>
                <%s>%s</%s>
            </div>
            """, iconName, iconSrc, tooltip, tag, title, tag);
        
        buffer.add(html);
    }
    
    public void formatTitleWithIcon(List<String> buffer, String iconSrc, String iconName, String title) {
        formatTitleWithIcon(buffer, iconSrc, iconName, title, "h5", null, null);
    }
    
    /**
     * 居中对齐文本
     */
    public void formatCenteredText(List<String> buffer, String text) {
        buffer.add("<div style=\"text-align: center;\">");
        formatText(buffer, text, null);
        buffer.add("</div>");
    }
    
    /**
     * 带提示的文本
     */
    public void formatWithTooltip(List<String> buffer, String text, String tooltip) {
        String html = String.format("""
            <div style="text-align: center;">
                <p class="text-muted"><span href="#" data-bs-toggle="tooltip" title="%s">%s</span></p>
            </div>
            """, tooltip, text);
        buffer.add(html);
    }
    
    /**
     * 格式化配方
     */
    public void formatRecipe(List<String> buffer, String recipeId) {
        if (recipeId != null && !recipeId.isEmpty()) {
            String text = String.format("%s: <code>%s</code>", translate(I18n.RECIPE), recipeId);
            formatWithTooltip(buffer, text, translate(I18n.RECIPE_ONLY_IN_GAME));
        }
    }

    public void fromKnappingRecipe(List<String> buffer, IPageDoubleRecipe page, String recipeId) {

    }

    public String translate(String... keys) {
        if (debugI18n) {
            return "{" + keys[0] + "}";
        }
        
        for (String key : keys) {
            if (langKeys.containsKey(key)) {
                return langKeys.get(key);
            }
        }

        if (!missingKeys.contains(keys[0])) {
            missingKeys.add(keys[0]);// FIXME remove later
            log.warn("Missing Translation Keys {}", Arrays.toString(keys));
        }
        return "{" + keys[0] + "}";
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

            ProjectUtil.require(width == 16 && height == 16,
                    "Icon must be 16x16: " + image);

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
        File outputFile = new File(outputRootDir, path);
        try {
            // Create output directory if it doesn't exist
            FileUtils.createParentDirectories(outputFile);

            // Save the image
            ImageIO.write(image, "png", outputFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save image: " + outputFile.getAbsolutePath(), e);
        }

        // Return relative path
        return "../../" + path;
    }

    /**
     * Saves multiple images to a .gif based on an identifier. Returns the relative path to that location.
     */
    public static String saveGif(String outputRootDir, String path, List<BufferedImage> images) throws IOException {
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("Images list cannot be empty");
        }

        // Create output directory if it doesn't exist
        File outputFile = new File(outputRootDir, path);
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

        return "../../" + path;
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
                result.setName(translate("item." + result.getKey(), "block." + result.getKey()));
            }
            return result;
        }

        if (item.indexOf('{') > 0) {
            log.warn("Item with NBT: {}", item);
            throw new InternalException("Item with NBT: " + item);
        }

        String name = null;
        String key = null;// translation key, if this needs to be re-translated
        List<String> items;

        boolean isItem = false;// this is to identify the itemId
        if (item.startsWith("#")) {
            name = String.format(translate(I18n.TAG), item);
            items = loadItemTag(item.substring(1));
        } else if (item.contains(",")) {
            items = Arrays.asList(item.split(","));
        } else {
            items = Collections.singletonList(item);
            isItem = true;
        }

        if (items.size() == 1) {
            key = items.getFirst().replace('/', '.').replace(':', '.');
            name = translate("item." + key, "block." + key);
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
                ItemImageResult fallback = new ItemImageResult("../../_images/placeholder_64.png", name, null);
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
                    images = images.stream().map(image -> {
                        if (image.getWidth() != 64 || image.getHeight() != 64) {
                            image = resizeImage(image, 64, 64);
                        }
                        return image;
                    }).toList();
                }

                path = saveGif(outputRootDir, "assets/generated/" + itemId + ".gif", images);
            }

            ItemImageResult result = new ItemImageResult(path, name, key);
            itemImageCache.put(item, result);
            return result;
        } catch (Exception e) {
            // TODO add e later
            log.error("Failed to create item image: {}, message: {}", item, e.getMessage());
            if (placeholder) {
                // Fallback to using the placeholder image
                ItemImageResult fallback = new ItemImageResult("../../_images/placeholder_64.png", name, null);
                itemImageCache.put(item, fallback);
                return fallback;
            }
            throw new InternalException("Failed to create item image: " + item);
        }
    }

    public List<String> loadItemTag(String tag) {
        return loader.loadItemTag(tag);
    }

    BufferedImage createItemImage(String itemId) {

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
                // Assume it's empty, and use a single layer item
                String layer = model.getTextures().get("base");
                return this.loader.loadTexture(layer);
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
            // single-layer item model
            String layer0 = model.getTextures().get("layer0");
            if (layer0 != null) {
                return loader.loadTexture(layer0);
            } else {
                log.warn("Item model no layer0: {}", itemId);
                missingImages.add(itemId);
                throw new InternalException("Item model no layer0: " + itemId);
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

    /// ///////////// block_loader
    ///

    private static final Map<String, String> CACHE = new HashMap<>();

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

    public String getMultiBlockImage(PageMultiMultiblock data) throws Exception {
        String key;
        List<BufferedImage> images = new ArrayList<>();

        if (!data.getMultiblocks().isEmpty()) {

            List<TFCMultiblockData> multiblocks = data.getMultiblocks();
            StringBuilder keyBuilder = new StringBuilder("multiblocks-");
            for (TFCMultiblockData block : multiblocks) {
                Pair<String, List<BufferedImage>> result = getMultiBlockImages(block);
                keyBuilder.append(result.getKey());
                images.addAll(result.getValue());
            }
            key = keyBuilder.toString();
        } else {
            throw new RuntimeException("Multiblock : Custom Multiblock");
        }

        if (images.isEmpty()) {
            log.error("Multiblock : No multiblocks found");
            throw new RuntimeException("Multiblock : No multiblocks found");
        }

        if (CACHE.containsKey(key)) {
            return CACHE.get(key);
        }

        String path;
        String blockId = nextId("block");// counting blocks
        if (images.size() == 1) {
            path = saveImage("assets/generated/" + blockId + ".png", images.getFirst());
        } else {
            path = saveGif(outputRootDir, "assets/generated/" + blockId + ".gif", images);
        }

        CACHE.put(key, path);
        return path;
    }

    public String getMultiBlockImage(PageMultiblock data) throws Exception {
         String key;
        List<BufferedImage> images;

        if (data.getMultiblock() != null) {
            PageMultiblockData multiblock = data.getMultiblock();

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
            path = saveGif(outputRootDir, "assets/generated/" + blockId + ".gif", images);
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
                    BufferedImage image = multiblock3DRenderer.render(pattern, data.getMapping());
                    return new Pair<>(Arrays.deepToString(pattern), List.of(image));
                } catch (Exception e) {
                    log.error("Failed loading multiblock image: {}, mapping: {}, message: {}", Arrays.deepToString(pattern), data.getMapping(), e.getMessage());
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
                log.error("Failed loading block image: {}, message: {}", b, e.getMessage());
            }
        }

        return new Pair<>(block, blockImages);
    }

    public BufferedImage getBlockImage(String blockState) throws Exception {
        Pair<String, Map<String, String>> parsedState = parseBlockState(blockState);
        String block = parsedState.getKey();
        Map<String, String> state = parsedState.getValue();

        Asset asset = loader.loadResource(block, "blockstates", "assets", ".json");
        // FIXME 使用BlockState 来反序列化，但需要处理好variants的变体，有时是list有时是object
        Map<String, Object> stateData = JsonUtils.readFile(asset.getInputStream(), new TypeToken<Map<String, Object>>() {}.getType());

        if (!stateData.containsKey("variants") || !(stateData.get("variants") instanceof Map)) {
            throw new RuntimeException("BlockState : Must be a 'variants' block state: '" + blockState + "'");
        }

        Map<String, Object> variants = (Map<String, Object>) stateData.get("variants");
        Map<String, Object> defaultModelData = null;
        Map<String, Object> modelData = null;

        for (Map.Entry<String, Object> entry : variants.entrySet()) {
            String key = entry.getKey();
            Map<String, Object> value = (Map<String, Object>) entry.getValue();

            if (defaultModelData == null) {
                defaultModelData = value;
            }

            Map<String, String> variantProperties = parseBlockProperties(key);
            if (state.entrySet().containsAll(variantProperties.entrySet())) {
                modelData = value;
                break;
            }
        }

        if (modelData == null) {
            if (state.isEmpty() && !variants.isEmpty()) {
                modelData = defaultModelData;
            } else {
                throw new RuntimeException("BlockState: No matching state found for '" + blockState + "' in " + variants);
            }
        }

        if (!modelData.containsKey("model")) {
            throw new RuntimeException("BlockState : No Model '" + block + "'");
        }

        String modelPath = (String) modelData.get("model");

        // load model
        BlockModel model = loader.loadModel(modelPath);

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

    public BufferedImage createBlockModelImage(String block, BlockModel model) throws Exception {
        if (model.getParent() == null) {
            throw new RuntimeException("Block Model : No Parent : '" + block + "'");
        }

        String parent = model.getParent();
        if (parent.indexOf(':') < 0) {
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
            BufferedImage oreAll = loader.loadTexture(textures.get("all"));
            BufferedImage overlay = loader.loadTexture(textures.get("overlay"));
            // 在Java中实现图像叠加
            BufferedImage combined = new BufferedImage(oreAll.getWidth(), oreAll.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = combined.createGraphics();
            g.drawImage(oreAll, 0, 0, null);
            g.drawImage(overlay, 0, 0, null);
            g.dispose();
            return createBlockModelProjection(combined, combined, combined, false);
        } else if (model.instanceOf("tfc:block/ore_column")) {
            BufferedImage side = loader.loadTexture(textures.get("side"));
            BufferedImage overlay = loader.loadTexture(textures.get("overlay"));
            // 在Java中实现图像叠加
            BufferedImage combined = new BufferedImage(side.getWidth(), side.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = combined.createGraphics();
            g.drawImage(side, 0, 0, null);
            g.drawImage(overlay, 0, 0, null);
            g.dispose();
            return createBlockModelProjection(combined, combined, combined, false);
        } else {
            try {
                return block3DRenderer.render(model);
            } catch (Exception e) {
                log.warn("Unsupported parent: {}@{}", parent, block);
                throw new RuntimeException("Block Model : Unknown Parent '" + parent + "' : at '" + block + "'");
            }
        }
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

    // 辅助类 - Pair
    public static class Pair<K, V> {
        private K key;
        private V value;

        public Pair(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public K getKey() { return key; }
        public V getValue() { return value; }
    }
}