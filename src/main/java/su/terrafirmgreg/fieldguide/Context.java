package su.terrafirmgreg.fieldguide;

import su.terrafirmgreg.fieldguide.asset.Asset;
import su.terrafirmgreg.fieldguide.asset.AssetLoader;
import su.terrafirmgreg.fieldguide.data.patchouli.BookCategory;
import su.terrafirmgreg.fieldguide.data.patchouli.BookEntry;
import su.terrafirmgreg.fieldguide.asset.ItemImageResult;
import su.terrafirmgreg.fieldguide.localization.I18n;
import su.terrafirmgreg.fieldguide.localization.Language;
import su.terrafirmgreg.fieldguide.localization.LazyLocalizationManager;
import su.terrafirmgreg.fieldguide.localization.LocalizationManager;
import su.terrafirmgreg.fieldguide.render.HtmlRenderer;
import su.terrafirmgreg.fieldguide.render.TextFormatter;
import su.terrafirmgreg.fieldguide.render.TextureRenderer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;
import java.util.List;

@Slf4j
@Data
public class Context {
    // 静态缓存，所有 Context 实例共享
    private static final Map<String, String> IMAGE_CACHE = new HashMap<>();

    String[] NAMESPACES = {"tfc", "minecraft", "forge", "tfg", "beneath", "afc", "firmalife", "create", "gtceu", "createdeco", "rnr", "ae2", "waterflasks", "sns", "firmaciv", "alekiships", "greate", "sophisticatedbackpacks", "tfcagedalcohol", "tfcbetterbf", "tfcchannelcasting", "tfchotornot"};

    // 实例字段
    private final AssetLoader loader;
    private final HtmlRenderer htmlRenderer;
    private final LocalizationManager localizationManager;
    private final TextureRenderer textureRenderer;

    private final String outputRootDir;// The output directory
    private final String basePath;// The web base path
    private final boolean debugI18n;
    
    private String outputLangDir;
    private Language lang;

    // 数据结构
    private Map<String, BookCategory> categoryMap = new HashMap<>();
    private Map<String, BookEntry> entryMap = new HashMap<>();
    private List<BookCategory> categories = new ArrayList<>();

    // ID 计数器
    private Map<String, Integer> lastUid = new HashMap<>();

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

    public Context(AssetLoader loader, String outputRootDir, String basePath, boolean debugI18n) throws IOException {
        this.loader = loader;
        this.outputRootDir = outputRootDir;
        this.outputLangDir = outputRootDir;
        this.basePath = basePath;
        this.debugI18n = debugI18n;

        this.localizationManager = new LazyLocalizationManager(loader);
        this.textureRenderer = new TextureRenderer(loader, localizationManager);

        this.htmlRenderer = new HtmlRenderer(localizationManager, outputRootDir);

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
    public Context withLang(Language lang) {
        this.lang = lang;
        this.outputLangDir = outputRootDir + "/" + lang.getKey();

        this.categoryMap = new HashMap<>();
        this.entryMap = new HashMap<>();
        this.categories = new ArrayList<>();

        this.localizationManager.switchLanguage(lang);
        return this;
    }

    public List<Asset> listAssets(String resourcePath) throws IOException {
        return loader.listAssets(resourcePath);
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
            TextFormatter.formatText(buffer, text, localizationManager.getKeybindings());

            if (search != null) {
                Map<String, String> searchData = new HashMap<>(search);
                searchData.put("content", TextFormatter.searchStrip(text));
                this.searchTree.add(searchData);
            }
        }
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
    public void formatCenteredText(List<String> buffer, String text, Map<String, String> search) {
        buffer.add("<div style=\"text-align: center;\">");
        formatText(buffer, text, search);
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
            String text = String.format("%s: <code>%s</code>", localizationManager.translate(I18n.RECIPE), recipeId);
            formatWithTooltip(buffer, text, localizationManager.translate(I18n.RECIPE_ONLY_IN_GAME));
        }
    }
}