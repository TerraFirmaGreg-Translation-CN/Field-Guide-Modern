package io.github.tfgcn.fieldguide;

import com.google.gson.reflect.TypeToken;
import com.madgag.gif.fmsware.AnimatedGifEncoder;
import io.github.tfgcn.fieldguide.asset.Asset;
import io.github.tfgcn.fieldguide.asset.AssetLoader;
import io.github.tfgcn.fieldguide.exception.InternalException;
import io.github.tfgcn.fieldguide.patchouli.BookCategory;
import io.github.tfgcn.fieldguide.patchouli.BookEntry;
import io.github.tfgcn.fieldguide.patchouli.page.IPageDoubleRecipe;
import io.github.tfgcn.fieldguide.patchouli.page.IPageWithText;
import io.github.tfgcn.fieldguide.asset.ItemImageResult;
import io.github.tfgcn.fieldguide.mc.BlockModel;
import io.github.tfgcn.fieldguide.renderer.TextFormatter;
import io.github.tfgcn.fieldguide.renderer.TextureRenderer;
import io.github.tfgcn.fieldguide.renderer.HtmlRenderer;
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
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

import static io.github.tfgcn.fieldguide.renderer.TextureRenderer.adjustBrightness;

@Slf4j
@Data
public class Context {
    // 静态缓存，所有 Context 实例共享
    private static final Map<String, String> IMAGE_CACHE = new HashMap<>();

    String[] NAMESPACES = {"tfc", "minecraft", "forge", "tfg", "beneath", "afc", "firmalife", "create", "gtceu", "createdeco", "rnr", "ae2", "waterflasks", "sns", "firmaciv", "alekiships", "greate", "sophisticatedbackpacks", "tfcagedalcohol", "tfcbetterbf", "tfcchannelcasting", "tfchotornot"};

    // 实例字段
    private final AssetLoader assetLoader;
    private final HtmlRenderer htmlRenderer;
    private final String outputRootDir;// The output directory
    private final String rootDir;// The root directory to fetch static assets from
    private final boolean debugI18n;
    
    private String outputDir;
    private String lang;

    // 数据结构
    private Map<String, BookCategory> categories = new HashMap<>();
    private Map<String, BookEntry> entries = new HashMap<>();
    private List<Map.Entry<String, BookCategory>> sortedCategories = new ArrayList<>();

    // 所有权管理
    private Map<String, String> categoryOwners = new HashMap<>();
    private Map<String, String> addonCategories = new HashMap<>();

    // language
    private Map<String, String> langFallbackKeys = new HashMap<>();// en_us
    public Set<String> missingKeys = new TreeSet<>();// FIXME remove later
    private Map<String, String> langKeys = new HashMap<>();

    private Map<String, String> keybindings = new HashMap<>();

    // ID 计数器
    private Map<String, Integer> lastUid = new HashMap<>();

    // 统计信息
    private int recipesFailed = 0;
    private int recipesPassed = 0;
    private int recipesSkipped = 0;
    private int itemsPassed = 0;
    private int itemsFailed = 0;
    private int blocksPassed = 0;
    private int blocksFailed = 0;
    
    // 搜索树
    private List<Map<String, Object>> searchTree = new ArrayList<>();

    private Map<String, ItemImageResult> itemImageCache = new HashMap<>();

    public Context(AssetLoader assetLoader, String outputRootDir, String rootDir, boolean debugI18n) throws IOException {
        this.assetLoader = assetLoader;
        this.outputRootDir = outputRootDir;
        this.outputDir = outputRootDir;
        this.rootDir = rootDir;
        this.debugI18n = debugI18n;

        this.htmlRenderer = new HtmlRenderer("assets/templates", outputRootDir);

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
        
        this.categories = new HashMap<>();
        this.entries = new HashMap<>();
        this.sortedCategories = new ArrayList<>();
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
        return assetLoader.listAssets(resourcePath);
    }

    private Map<String, String> loadLang(String lang) {
        Map<String, String> langMap = new HashMap<>();
        // load mod lang
        for (String namespace : NAMESPACES) {
            Map<String, String> map = assetLoader.loadLang(namespace, lang);
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

    public boolean hasEntry(String entryId) {
        return this.entries.containsKey(entryId);
    }

    public void addEntry(String categoryId, String entryId, BookEntry entry, Map<String, Object> search) {
        try {
            this.entries.put(entryId, entry);
            this.categories.get(categoryId).getEntries().add(entryId);
            this.searchTree.add(search);
        } catch (Exception e) {
            // FIXME
            log.warn("Add Entry: {} : {}, {}", categoryId, entryId, e.getMessage());
        }
    }
    
    /**
     * 对分类和条目进行排序
     */
    public void sort() {
        // 排序分类
        this.sortedCategories = new ArrayList<>(this.categories.entrySet());
        this.sortedCategories.sort((a, b) -> {
            BookCategory catA = a.getValue();
            BookCategory catB = b.getValue();
            if (catA.getSort() != catB.getSort()) {
                return Integer.compare(catA.getSort(), catB.getSort());
            }
            return a.getKey().compareTo(b.getKey());
        });

        // 对每个分类下的条目排序
        for (Map.Entry<String, BookCategory> entry : this.sortedCategories) {
            BookCategory cat = entry.getValue();
            List<String> sortedEntryNames = new ArrayList<>(cat.getEntries());
            sortedEntryNames.sort((a, b) -> {
                BookEntry entryA = this.entries.get(a);
                BookEntry entryB = this.entries.get(b);
                if (entryA.getSort() != entryB.getSort()) {
                    return Integer.compare(entryA.getSort(), entryB.getSort());
                }
                return a.compareTo(b);
            });
            
            List<Map.Entry<String, BookEntry>> sortedEntries = new ArrayList<>();
            for (String entryName : sortedEntryNames) {
                sortedEntries.add(Map.entry(entryName, this.entries.get(entryName)));
            }
            cat.setSortedEntries(sortedEntries);
        }
    }
    
    public void formatText(List<String> buffer, String text, Map<String, Object> search) {
        if (text != null && !text.isEmpty()) {
            TextFormatter.formatText(buffer, text, this.keybindings);

            if (search != null) {
                Map<String, Object> searchData = new HashMap<>(search);
                searchData.put("content", text);
                this.searchTree.add(searchData);
            }
        }
    }

    public void formatText(List<String> buffer, IPageWithText page) {
        formatText(buffer, page.getText(), null);
    }
    
    public void formatTitle(List<String> buffer, String title, Map<String, Object> search) {
        if (title != null && !title.isEmpty()) {
            String stripped = TextFormatter.stripVanillaFormatting(title);
            buffer.add("<h5>" + stripped + "</h5>\n");

            if (search != null) {
                Map<String, Object> searchData = new HashMap<>(search);
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
                                   String tooltip, Map<String, Object> search) {
        String title = iconName;
        if (inTitle != null && !inTitle.isEmpty()) {
            title = TextFormatter.stripVanillaFormatting(inTitle);
            if (iconName == null || iconName.isEmpty()) {
                iconName = title;
            }
            if (search != null) {
                Map<String, Object> searchData = new HashMap<>(search);
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
            Asset asset = assetLoader.loadResource(image, "assets", ".png");
            BufferedImage img = ImageIO.read(asset.getInputStream());

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

            String ref = saveImage(nextId("image"), cropped);
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
            BufferedImage img = assetLoader.loadTexture(image);

            int width = img.getWidth();
            int height = img.getHeight();

            ProjectUtil.require(width == 16 && height == 16,
                    "Icon must be 16x16: " + image);

            // 调整到64x64以匹配物品图标尺寸
            BufferedImage resized = resizeImage(img, 64, 64);
            String ref = saveImage(nextId("image"), resized);
            IMAGE_CACHE.put(image, ref);
            return ref;
        } catch (Exception e) {
            throw new InternalException("Failed to convert icon: " + image + " - " + e.getMessage());
        }
    }
    
    /**
     * 图片缩放工具方法
     */
    private BufferedImage resizeImage(BufferedImage original, int targetWidth, int targetHeight) {
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resized.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

        g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        return resized;
    }


    /**
     * Saves an image to a location based on an identifier.
     * @param id the resource path identifier
     * @param image the image to save
     * @return the relative id to that location.
     */
    public String saveImage(String id, BufferedImage image) {
        // Parse resource location (remove namespace if present)
        String processedPath;
        if (id.contains(":")) {
            processedPath = id.split(":")[1];
        } else {
            processedPath = id;
        }

        // Replace slashes with underscores and join with '_images'
        String filename = processedPath.replace("/", "_");
        String outputPath = Paths.get("_images", filename).toString();

        // Ensure .png extension
        if (!outputPath.toLowerCase().endsWith(".png")) {
            outputPath += ".png";
        }

        File outputFile = new File(outputRootDir, outputPath);
        try {
            // Create output directory if it doesn't exist
            FileUtils.createParentDirectories(outputFile);

            // Save the image
            ImageIO.write(image, "png", outputFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save image: " + outputFile.getAbsolutePath(), e);
        }

        // Return relative path
        return Paths.get("../../", outputPath).toString();
    }

    /**
     * Saves multiple images to a .gif based on an identifier. Returns the relative path to that location.
     */
    public String saveGif(String path, List<BufferedImage> images) throws IOException {
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("Images list cannot be empty");
        }

        // Process path similar to the Python version
        String processedPath = processPath(path);

        // Create output directory if it doesn't exist
        File outputFile = new File(outputDir, processedPath);
        outputFile.getParentFile().mkdirs();

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

        return "../../" + processedPath;
    }

    private String processPath(String path) {
        // Remove .png suffix and add .gif
        String processed = path.replace(".png", "") + ".gif";
        // Replace slashes with underscores and add to _images directory
        processed = "_images/" + processed.replace("/", "_");
        return processed;
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

        // TODO get from cache
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

        if (item.startsWith("#")) {
            name = translate(I18n.TAG) + item;
            items = loadItemTag(item.substring(1));
        } else if (item.contains(",")) {
            items = Arrays.asList(item.split(","));
        } else {
            items = Collections.singletonList(item);
        }

        if (items.size() == 1) {
            key = items.get(0).replace('/', '.').replace(':', '.');
            name = translate("item." + key, "block." + key);
        }

        try {
            // Create image for each item.
            List<BufferedImage> images = new ArrayList<>();
            for (String it : items) {
                try {
                    images.add(createItemImage(it));
                } catch (Exception e) {
                    log.warn("Failed to create item image for {}", it, e);
                }
            }

            if (images.isEmpty()) {
                throw new InternalException("Failed to create item image for: " + item);
            }

            String path;
            if (images.size() == 1) {
                images.get(0);
                path = saveImage(nextId("item"), images.get(0));
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

                path = saveGif(nextId("item"), images);
            }

            ItemImageResult result = new ItemImageResult(path, name, key);
            itemImageCache.put(item, result);
            return result;
        } catch (Exception e) {
            log.warn("Failed to create item image: {}", item, e);
            if (placeholder) {
                // Fallback to using the placeholder image
                ItemImageResult fallback = new ItemImageResult("_images/item_placeholder.png", name, null);
                itemImageCache.put(item, fallback);
            }
            throw new InternalException("Failed to create item image: " + item);
        }
    }

    public List<String> loadItemTag(String tag) {
        return assetLoader.loadItemTag(tag);
    }

    BufferedImage createItemImage(String itemId) {
        BlockModel model = loadItemModel(itemId);
        if (model.getParent() == null) {
            log.warn("Item model no parent: {}, model: {}", itemId, model);
            // TODO 支持无parent的模型
            throw new InternalException("Item model no parent: " + itemId);
        }
        // TODO

        if (model.getLoader() != null) {
            String loader = model.getLoader();
            if ("tfc:contained_fluid".equals(loader)) {
                // Assume it's empty, and use a single layer item
                String layer = model.getTextures().get("base");
                return assetLoader.loadTexture(layer);
            } else {
                log.error("Unknown loader: {} @ {}, model: {}", loader, itemId, model);
            }
        }

        String parent = model.getParent();
        if (parent.indexOf(':') < 0) {
            parent = "minecraft:" + parent;
        }
        // FIXME 使用更通用的方式来判断模型是 item 还是 model
        // FIXME 甚至干脆修改渲染方法，实现真正的继承层次 3D 渲染。
        if ("minecraft:item/generated".equals(parent) ||
                "minecraft:item/handheld".equals(parent) ||
                "minecraft:item/handheld_rod".equals(parent) ||
                "tfc:item/handheld_flipped".equals(parent) ||
                "item/generated".equals(parent)
        ) {
            // single-layer item model
            String layer0 = model.getTextures().get("layer0");
            return assetLoader.loadTexture(layer0);
        } else if (parent.startsWith("tfc:block/") || parent.startsWith("minecraft:block/") || parent.startsWith("beneath:block/")) {
            // Block model
            // TODO remove the try-catch
            try {
                Asset modelAsset = assetLoader.loadResource(parent, "models", "assets", ".json");
                BlockModel blockModel = JsonUtils.readFile(modelAsset.getInputStream(), BlockModel.class);

                BufferedImage img = createBlockModelImage(itemId, blockModel);
                img = resizeImage(img, 64, 64);
                return img;
            } catch (Exception e) {
                log.error("Failed load model {} @ {}, model: {}", parent, itemId, model, e);
                throw new InternalException("Failed load model " + parent + " @ " + itemId);
            }
        } else {
            log.error("Unknown Parent {} @ {}, model: {}", parent, itemId, model);
            throw new InternalException("Unknown Parent " + parent + " @ " + itemId);
        }
    }

    private BlockModel loadItemModel(String itemId) {
        Asset asset = assetLoader.loadResource(itemId, "models/item", "assets", ".json");
        try {
            return JsonUtils.readFile(asset.getInputStream(), BlockModel.class);
        } catch (Exception e) {
            log.warn("Failed to load item model: {}", itemId, e);
            throw new InternalException("Failed to load item model: " + itemId);
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

    public String getMultiBlockImage(Context context, Map<String, Object> data) throws Exception {
        String key;
        List<BufferedImage> images = new ArrayList<>();

        if (data.containsKey("multiblocks")) {
            List<Map<String, Object>> multiblocks = (List<Map<String, Object>>) data.get("multiblocks");
            StringBuilder keyBuilder = new StringBuilder("multiblocks-");
            for (Map<String, Object> block : multiblocks) {
                Pair<String, List<BufferedImage>> result = getMultiBlockImages(block);
                keyBuilder.append(result.getKey());
                images.addAll(result.getValue());
            }
            key = keyBuilder.toString();
        } else if (data.containsKey("multiblock")) {
            Map<String, Object> multiblock = (Map<String, Object>) data.get("multiblock");
            Pair<String, List<BufferedImage>> result = getMultiBlockImages(multiblock);
            key = result.getKey();
            images = result.getValue();
        } else {
            throw new RuntimeException("Multiblock : Custom Multiblock '" + data.get("multiblock_id") + "'");
        }

        if (CACHE.containsKey(key)) {
            return CACHE.get(key);
        }

        String path;
        if (images.size() == 1) {
            path = saveImage(nextId("block"), images.get(0));
        } else {
            path = saveGif(nextId("block"), images);
        }

        CACHE.put(key, path);
        return path;
    }

    public Pair<String, List<BufferedImage>> getMultiBlockImages(Map<String, Object> data) throws Exception {
        if (!data.containsKey("pattern")) {
            throw new RuntimeException("Multiblock : No 'pattern' field");
        }

        List<List<String>> pattern = (List<List<String>>) data.get("pattern");
        List<List<String>> validPattern1 = Arrays.asList(
                Arrays.asList("X"),
                Arrays.asList("0")
        );
        List<List<String>> validPattern2 = Arrays.asList(
                Arrays.asList("X"),
                Arrays.asList("Y"),
                Arrays.asList("0")
        );

        if (!pattern.equals(validPattern1) && !pattern.equals(validPattern2)) {
            throw new RuntimeException("Multiblock : Complex Pattern '" + pattern + "'");
        }

        String block = (String) ((Map<String, Object>) data.get("mapping")).get("X");
        List<String> blocks;

        if (block.startsWith("#")) {
            // FIXME
            // blocks = loadBlockTag(block.substring(1));
            blocks = List.of();
        } else {
            blocks = Arrays.asList(block);
        }

        List<BufferedImage> blockImages = new ArrayList<>();
        for (String b : blocks) {
            blockImages.add(getBlockImage(b));
        }

        return new Pair<>(block, blockImages);
    }

    public BufferedImage getBlockImage(String blockState) throws Exception {
        Pair<String, Map<String, String>> parsedState = parseBlockState(blockState);
        String block = parsedState.getKey();
        Map<String, String> state = parsedState.getValue();

        Asset asset = assetLoader.loadResource(block, "blockstates", "assets", ".json");
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
        Asset modelAsset = assetLoader.loadResource(modelPath, "models", "assets", ".json");
        BlockModel model = JsonUtils.readFile(modelAsset.getInputStream(), BlockModel.class);

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

        switch (parent) {
            case "minecraft:block/cube_all":
                BufferedImage textureAll = assetLoader.loadTexture(textures.get("all"));
                return createBlockModelProjection(textureAll, textureAll, textureAll, false);

            case "minecraft:block/cube_column":
                BufferedImage side = assetLoader.loadTexture(textures.get("side"));
                BufferedImage end = assetLoader.loadTexture(textures.get("end"));
                return createBlockModelProjection(side, side, end, false);

            case "minecraft:block/cube_column_horizontal":
                BufferedImage sideH = assetLoader.loadTexture(textures.get("side"));
                BufferedImage endH = assetLoader.loadTexture(textures.get("end"));
                return createBlockModelProjection(endH, sideH, sideH, true);

            case "minecraft:block/template_farmland":
                BufferedImage dirt = assetLoader.loadTexture(textures.get("dirt"));
                BufferedImage top = assetLoader.loadTexture(textures.get("top"));
                return createBlockModelProjection(dirt, dirt, top, false);

            case "minecraft:block/slab":
                BufferedImage topSlab = assetLoader.loadTexture(textures.get("top"));
                BufferedImage sideSlab = assetLoader.loadTexture(textures.get("side"));
                return createSlabBlockModelProjection(sideSlab, sideSlab, topSlab);

            case "minecraft:block/crop":
                BufferedImage crop = assetLoader.loadTexture(textures.get("crop"));
                return createCropModelProjection(crop);

            case "tfc:block/ore":
                BufferedImage oreAll = assetLoader.loadTexture(textures.get("all"));
                BufferedImage overlay = assetLoader.loadTexture(textures.get("overlay"));
                // 在Java中实现图像叠加
                BufferedImage combined = new BufferedImage(oreAll.getWidth(), oreAll.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = combined.createGraphics();
                g.drawImage(oreAll, 0, 0, null);
                g.drawImage(overlay, 0, 0, null);
                g.dispose();
                return createBlockModelProjection(combined, combined, combined, false);

            default:
                log.warn("Block Model: Unknown parent: {} @ {}, model: {}", parent, block);
                throw new RuntimeException("Block Model : Unknown Parent '" + parent + "' : at '" + block + "'");
        }
    }

    public BufferedImage createBlockModelProjection(BufferedImage left, BufferedImage right, BufferedImage top, boolean rotate) {
        if (rotate) {
            right = rotateImage(right, 90);
            top = rotateImage(top, 90);
        }

        BufferedImage result = TextureRenderer.createBlockImage(left, right, top);
        return result;
    }

    public static BufferedImage createSlabBlockModelProjection(BufferedImage left, BufferedImage right, BufferedImage top) {
        // crop
        left = cropRetainingPosition(left, 0, 8, 16, 16);
        right = cropRetainingPosition(right, 0, 8, 16, 16);

        // 合并图像
        BufferedImage result = TextureRenderer.createBlockImage(left, right, top);
        return result;
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
        transform.translate(newWidth / 2, newHeight / 2);
        transform.rotate(radians);
        transform.translate(-image.getWidth() / 2, -image.getHeight() / 2);

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