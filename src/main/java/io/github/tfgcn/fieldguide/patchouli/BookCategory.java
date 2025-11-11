package io.github.tfgcn.fieldguide.patchouli;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @see <a href="https://vazkiimods.github.io/Patchouli/docs/reference/category-json>Category JSON Format</a>
 */
@Data
public class BookCategory {
    private List<String> entries = new ArrayList<>();
    private List<Map.Entry<String, BookEntry>> sortedEntries = new ArrayList<>();

    private String id;
    /**
     * The name of this category.
     */
    private String name;// required
    /**
     * The description for this category.
     * Can be formatted using the same syntax as book entries.
     * @see <a href="https://vazkiimods.github.io/Patchouli/docs/patchouli-basics/text-formatting">Text Formatting 101</a>
     */
    private String description;// required
    /**
     * The icon for this category.
     *
     * <p>This can either be an ItemStack String, if you want an item to be the icon, or a resource location pointing to a square texture. If you want to use a resource location, make sure to end it with .png.</p>
     *
     * @see <a href="https://vazkiimods.github.io/Patchouli/docs/patchouli-advanced/itemstack-format">ItemStack String Format</a>
     */
    private String icon;// required
    /**
     * The parent category to this one.
     * <p>If this is a sub-category, simply put the name of the category this is a child to here. If not, don't define it. This should be fully-qualified and of the form `domain:name` where `domain` is the same as the domain of your Book ID.</p>
     */
    private String parent;
    /**
     * A config flag expression that determines whether this category should exist or not. See Using Config Flags for more info on config flags.
     * @see <a href="https://vazkiimods.github.io/Patchouli/docs/patchouli-basics/config-gating">Config Gating</a>
     */
    private String flag;
    /**
     * The sorting number for this category.
     * Defaults to 0. Categories are sorted in the main page from lowest sorting number to highest, so if you define this in every category you make, you can set what order they display in.
     */
    @SerializedName("sortnum")
    private int sort = 0;

    /**
     * Defaults to false.
     * Set this to true to make this category a secret category. Secret categories don't display a locked icon when locked, and instead will not display at all until unlocked.
     */
    private boolean secret = false;

    // Utility methods for adding entries
    public void addEntry(String entryId) {
        this.entries.add(entryId);
    }

    public void addSortedEntry(String entryId, BookEntry entry) {
        this.sortedEntries.add(Map.entry(entryId, entry));
    }

    @Override
    public String toString() {
        return name;
    }
}