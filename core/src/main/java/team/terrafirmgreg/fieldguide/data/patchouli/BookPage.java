package team.terrafirmgreg.fieldguide.data.patchouli;

import com.google.gson.JsonObject;
import lombok.Data;

/**
 * @see <a href="https://vazkiimods.github.io/Patchouli/docs/patchouli-basics/page-types/">Default Page types</a>
 */
@Data
public class BookPage {

    /**
     * What type this page is.
     * This isn't used by the page itself, but rather by the loader to determine what page
     * should be loaded. For example, if you want a text page, you set this to patchouli:text.
     * This should be a fully-qualified namespaced ID in 1.17 and later and of the form
     * namespace:name. For the built-in page types defined here, the namespace is patchouli.
     * In 1.16 or earlier version, you should leave out everything up to and including the
     * colon, so it would be text.
     */
    protected String type;

    /**
     * A config flag expression that determines whether this page should exist or not.
     * See Using Config Flags for more info on config flags.
     */
    protected String flag;

    /**
     * A resource location to point at, to make a page appear when that advancement is completed.
     */
    protected String advancement;

    /**
     * An anchor can be used elsewhere to refer to this specific page in an internal link.
     * See Text Formatting 101 for more details about internal links.
     * @see <a href="https://vazkiimods.github.io/Patchouli/docs/patchouli-basics/text-formatting">Text Formatting 101</a>
     */
    protected String anchor;

    private transient JsonObject jsonObject;
}
