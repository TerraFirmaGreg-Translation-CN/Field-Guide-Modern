package su.terrafirmgreg.fieldguide.data.patchouli;

import com.google.gson.annotations.SerializedName;
import su.terrafirmgreg.fieldguide.asset.Asset;
import su.terrafirmgreg.fieldguide.asset.AssetSource;
import su.terrafirmgreg.fieldguide.localization.Language;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Patchouli book
 * @see <a href="https://vazkiimods.github.io/Patchouli/docs/reference/book-json/">Book JSON Format</a>
 */
@Slf4j
@Data
public class Book {

    /**
     * The name of the book that will be displayed in the book item and the GUI.
     * For modders, this can be a localization key.
     */
    private String name; // required

    /**
     * The text that will be displayed in the landing page of your book.
     * This text can be formatted. For modders, this can be a localization key.
     */
    @SerializedName("landing_text")
    private String landingText; // required

    /**
     * If true, book contents (categories, entries, and templates) are loaded via the resource system,
     * and thus must live in a resource pack or mod resources.
     * Starting in 1.20, must be set to true for books declared outside of .minecraft/patchouli_books.
     */
    @SerializedName("use_resource_pack")
    private Boolean useResourcePack;

    /**
     * The texture for the background of the book GUI.
     * Recommended to use built-in ones: patchouli:textures/gui/book_blue.png, book_brown.png, etc.
     * Default value: patchouli:textures/gui/book_brown.png
     */
    @SerializedName("book_texture")
    private String bookTexture;

    /**
     * The texture for the page filler (the cube thing that shows up on entries with an odd number of pages).
     * Define if you want something else than the cube to fill your empty pages.
     */
    @SerializedName("filter_texture")
    private String filterTexture;

    /**
     * The texture for the crafting entry elements.
     * Define if you want custom backdrops for these.
     */
    @SerializedName("crafting_texture")
    private String craftingTexture;

    /**
     * The model for the book's item, in the same format as they appear in resource packs.
     * Patchouli provides several models: patchouli:book_blue, book_brown (default), etc.
     */
    private String model;

    /**
     * The color of regular text, in hex ("RRGGBB", # not necessary).
     * Defaults to "000000".
     */
    @SerializedName("text_color")
    private String textColor;

    /**
     * The color of header text, in hex ("RRGGBB", # not necessary).
     * Defaults to "333333".
     */
    @SerializedName("header_color")
    private String headerColor;

    /**
     * The color of the book nameplate in the landing page, in hex ("RRGGBB", # not necessary).
     * Defaults to "FFDD00".
     */
    @SerializedName("nameplate_color")
    private String nameplateColor;

    /**
     * The color of link text, in hex ("RRGGBB", # not necessary).
     * Defaults to "0000EE".
     */
    @SerializedName("link_color")
    private String linkColor;

    /**
     * The color of hovered link text, in hex ("RRGGBB", # not necessary).
     * Defaults to "8800EE".
     */
    @SerializedName("link_hover_color")
    private String linkHoverColor;

    /**
     * The color of advancement progress bar, in hex ("RRGGBB", # not necessary).
     * Defaults to "FFFF55".
     */
    @SerializedName("progress_bar_color")
    private String progressBarColor;

    /**
     * The color of advancement progress bar's background, in hex ("RRGGBB", # not necessary).
     * Defaults to "DDDDDD".
     */
    @SerializedName("progress_bar_background")
    private String progressBarBackground;

    /**
     * The sound effect played when opening this book.
     * This is a resource location pointing to the sound.
     */
    @SerializedName("open_sound")
    private String openSound;

    /**
     * The sound effect played when flipping through pages in this book.
     * This is a resource location pointing to the sound.
     */
    @SerializedName("flip_sound")
    private String flipSound;

    /**
     * The icon to display for the Book Index.
     * This can either be an ItemStack String or a resource location pointing to a square texture.
     * Defaults to the book's icon.
     */
    @SerializedName("index_icon")
    private String indexIcon;

    /**
     * Available in Patchouli 1.18.2-68 or above.
     * Defaults to false. If true, marks this book as a pamphlet.
     */
    private Boolean pamphlet;

    /**
     * Defaults to true. Set to false to disable the advancement progress bar,
     * even if advancements are enabled.
     */
    @SerializedName("show_progress")
    private Boolean showProgress;

    /**
     * The "edition" of the book. Defaults to "0".
     * Setting this to any other numerical value will display "X Edition" in tooltip and landing page.
     * Non-numerical values display "Writer's Edition".
     */
    private String version;

    /**
     * A subtitle for your book, which will display in the tooltip and below the book name
     * in the landing page if "version" is set to "0" or not set.
     */
    private String subtitle;

    /**
     * The creative tab to display your book in. Defaults to null (no tab).
     * For vanilla tabs, use values like: building_blocks, colored_blocks, etc. (1.20)
     */
    @SerializedName("creative_tab")
    private String creativeTab;

    /**
     * The ID of the advancements tab you want this book to be associated to.
     * If defined, an Advancements button will show up in the landing page.
     */
    @SerializedName("advancements_tab")
    private String advancementsTab;

    /**
     * Defaults to false. Set this to true if you don't want Patchouli to make a book item for your book.
     * Use only if you're a modder and need a custom Item class.
     */
    @SerializedName("dont_generate_book")
    private Boolean dontGenerateBook;

    /**
     * If you have a custom book, set it here. This is an ItemStack String.
     */
    @SerializedName("custom_book_item")
    private String customBookItem;

    /**
     * Defaults to true. Set it to false if you don't want your book to show toast notifications
     * when new entries are available.
     */
    @SerializedName("show_toasts")
    private Boolean showToasts;

    /**
     * Defaults to false. Set it to true to use the vanilla blocky font rather than the slim font.
     */
    @SerializedName("use_blocky_font")
    private Boolean useBlockyFont;

    /**
     * Default false. If set to true, attempts to look up category, entry, and page titles
     * as well as any page text in the lang files before rendering.
     */
    private Boolean i18n;

    /**
     * Formatting macros this book should use.
     * See Text Formatting 101 for more info on how you can define these and what they do.
     */
    private Map<String, String> macros;

    /**
     * Default false. When set to true, opening any GUI from this book will pause the game in singleplayer.
     */
    @SerializedName("pause_game")
    private Boolean pauseGame;

    /**
     * Added in Patchouli 1.18.2-71
     * Allows the textOverflow config option to be customized per-book.
     * Values: overflow, resize, truncate
     */
    @SerializedName("text_overflow_mode")
    private String textOverflowMode;

    /**
     * For versions before 1.20. Extension books have been replaced with resource-pack-based overrides in 1.20+.
     * Marks this book as an extension to the specified target book.
     */
    @SerializedName("extend")
    private String extend;

    /**
     * Defaults to true. Set it to false if you want to lock your book from being extended by other books.
     */
    @SerializedName("allow_extensions")
    private Boolean allowExtensions;

    private transient Language language;
    private transient AssetSource assetSource;
    private transient List<BookCategory> categories = new ArrayList<>();
    private transient Map<String, BookCategory> categoryMap = new TreeMap<>();
    private transient List<BookEntry> entries = new ArrayList<>();
    private transient Map<String, BookEntry> entryMap = new TreeMap<>();

    public void setAssetSource(Asset asset) {
        this.assetSource = asset.getSource();
    }

    public void addCategory(BookCategory category) {
        BookCategory exist = categoryMap.get(category.getId());
        if (exist != null) {
            log.debug("Override category: {}, {} -> {}", category.getId(), exist.getAssetSource(), category.getAssetSource());
        } else {
            categories.add(category);
            categoryMap.put(category.getId(), category);
        }
    }

    public void addEntry(BookEntry entry) {
        BookEntry exist = entryMap.get(entry.getId());
        if (exist != null) {
            log.debug("Override entry: {}, {} -> {}", entry.getId(), exist.getAssetSource(), entry.getAssetSource());
        } else {
            BookCategory category = categoryMap.get(entry.getCategoryId());
            if (category != null) {
                entries.add(entry);
                entryMap.put(entry.getId(), entry);
                category.addEntry(entry);
            } else {
                log.warn("Entry {} has an unknown category: {}", entry.getId(), entry.getCategoryId());
            }
        }
    }

    public void sort() {
        this.categories.sort(BookCategory::compareTo);
        for (BookCategory cat : this.categories) {
            cat.getEntries().sort(BookEntry::compareTo);
        }
    }

    public void report() {
        System.out.printf("===== Report %s =====\n", language);
        System.out.printf("Total: %d categories, %d entries\n", getCategories().size(), getEntries().size());
        for (BookCategory category : getCategories()) {
            System.out.printf("%s - <%s> (%d entries): %s\n", category.getId(), category.getName(), category.getEntries().size(), category.getAssetSource().getSourceId());
            for (BookEntry entry : category.getEntries()) {
                System.out.printf("  %s/%s - <%s> (%d pages): %s\n", entry.getCategoryId(), entry.getRelId(), entry.getName(), entry.getPages().size(), entry.getAssetSource().getSourceId());
            }
        }
    }
}