package io.github.tfgcn.fieldguide.patchouli;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class BookEntry {
    private List<String> buffer = new ArrayList<>();

    /**
     * The "entry ID" of an entry is the path necessary to get to it from
     * `/en_us/entries`. So if your entry is in `/en_us/entries/misc/cool_stuff.json`,
     * its ID would be `patchouli:misc/cool_stuff`.
     */
    private String id = "";    // The full name, <category>/<entry>
    private String relId = ""; // Just the entry name, <entry>

    /**
     * The localized name of this entry
     */
    private String name = "";// required
    /**
     * The category this entry belongs to.
     * This must be set to one of your categories' ID. For best results, use a
     * fully-qualified ID that includes your book namespace `tfg:get_started`.
     * In the future this will be enforced.
     */
    private String category = "";// required

    /**
     * The icon for this entry.
     * This can either be an ItemStack String, if you want an item to be the icon,
     * or a resource location pointing to a square texture.
     * If you want to use a resource location, make sure to end it with .png
     */
    private String icon = "";// required

    /**
     * The array of pages for this entry
     * See Default Page Types for the page types that Patchouli comes with and what data each one requires,
     * or Using Templates for instructions on how to create your own.
     * @see <a href="https://vazkiimods.github.io/Patchouli/docs/patchouli-basics/page-types">Default Page Types</a>
     * @see <a href="https://vazkiimods.github.io/Patchouli/docs/patchouli-basics/templates">Templating</a>
     */
    private List<BookPage> pages;// required

    /**
     * The name of the advancement you want this entry to be locked behind.
     * See Locking Content with Advancements for more info on locking content.
     * @see <a href="https://vazkiimods.github.io/Patchouli/docs/patchouli-basics/advancement-locking">Gating Content with Advancements</a>
     */
    private String advancement = "";

    /**
     * A config flag expression that determines whether this entry should exist or not. See Using Config Flags for more info on config flags.
     */
    private String flag = "";

    /**
     * Defaults to false.
     * If set to true, the entry will show up with an italicized name, and will always show up at the top of the category.
     * Use for really important entries you want to show up at the top.
     */
    private Boolean priority = false;

    /**
     * Defaults to false.
     * Set this to true to make this entry a secret entry.
     * Secret entries don't display as "Locked" when locked, and instead will not display at all until unlocked.
     * Secret entries do not count for % completion of the book, and when unlocked will instead show as an additional line in the tooltip.
     */
    private Boolean secret = false;

    /**
     * The sorting number for this entry. Defaults to 0.
     * Entries with the same sorting number are sorted alphabetically, whereas entries with different
     * sorting numbers are sorted from lowest to highest. Priority entries always show up first.
     *
     * It's recommended you do not use this, as breaking the alphabetical sorting order can make things confusing, but it's left as an option.
     */
    @SerializedName("sortnum")
    private int sort = 0;

    /**
     * The ID of an advancement the player needs to do to "complete" this entry.
     * The entry will show up at the top of the list with a (?) icon next to it until this advancement is complete.
     * This can be used as a quest system or simply to help guide players along a starting path.
     */
    private String turnin;

    /**
     * Additional list of items this page teaches the crafting process for, for use with the in-world
     * right click and quick lookup feature. Keys are ItemStack strings, values are 0-indexed page numbers.
     */
    @SerializedName("extra_recipe_mappings")
    private Map<String, Integer> extraRecipeMappings;

    /**
     * The color of the link to this entry from its category, in hex ("RRGGBB", # not necessary).
     * Defaults to `book.text_color = 000000`.
     */
    @SerializedName("entry_color")
    private String entryColor;

    private String iconPath = "";
    private String iconName = "";

    // Equivalent to Python's push method
    public void push(String text) {
        buffer.add(text);
    }

    // Equivalent to Python's __str__ method
    @Override
    public String toString() {
        return name;
    }
}