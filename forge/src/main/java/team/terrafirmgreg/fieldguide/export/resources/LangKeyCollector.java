package team.terrafirmgreg.fieldguide.export.resources;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import team.terrafirmgreg.fieldguide.export.patchouli.Book;
import team.terrafirmgreg.fieldguide.export.patchouli.BookCategory;
import team.terrafirmgreg.fieldguide.export.patchouli.BookEntry;
import team.terrafirmgreg.fieldguide.export.patchouli.BookPage;
import team.terrafirmgreg.fieldguide.export.scan.BookScanResult;
import team.terrafirmgreg.fieldguide.localization.I18n;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the set of {@code lang/*.json} keys required for a closure export (book + recipes + registry refs).
 */
public final class LangKeyCollector {

    private static final Pattern PATCHOULI_MACRO = Pattern.compile("\\$\\(([^)]+)\\)");

    private LangKeyCollector() {}

    public static Set<String> collect(
            Book book,
            BookScanResult scan,
            Set<String> items,
            Set<String> blocks,
            Set<String> fluids,
            Iterable<JsonElement> recipeJsonRoots) {
        Set<String> keys = new TreeSet<>();
        addSiteUiKeys(keys);
        if (items != null) {
            for (String id : items) {
                addRegistryKeys(keys, id, "item");
            }
        }
        if (blocks != null) {
            for (String id : blocks) {
                addRegistryKeys(keys, id, "block");
            }
        }
        if (fluids != null) {
            for (String id : fluids) {
                addRegistryKeys(keys, id, "fluid");
            }
        }
        if (book != null) {
            collectFromBook(book, keys);
        }
        if (scan != null) {
            for (String entity : scan.getEntities()) {
                addRegistryKeys(keys, entity, "entity");
            }
        }
        if (recipeJsonRoots != null) {
            for (JsonElement recipe : recipeJsonRoots) {
                collectFromRecipe(recipe, keys);
            }
        }
        return keys;
    }

    private static void addSiteUiKeys(Set<String> keys) {
        keys.add(I18n.TITLE);
        keys.add(I18n.SHORT_TITLE);
        keys.add(I18n.INDEX);
        keys.add(I18n.CONTENTS);
        keys.add(I18n.GITHUB);
        keys.add(I18n.DISCORD);
        keys.add(I18n.CATEGORIES);
        keys.add(I18n.HOME);
        keys.add(I18n.MULTIBLOCK);
        keys.add(I18n.MULTIBLOCK_ONLY_IN_GAME);
        keys.add(I18n.RECIPE);
        keys.add(I18n.RECIPE_ONLY_IN_GAME);
        keys.add(I18n.ITEM);
        keys.add(I18n.ITEMS);
        keys.add(I18n.ITEM_ONLY_IN_GAME);
        keys.add(I18n.TICKS);
        keys.add(I18n.TAG);
        for (String keybind : I18n.KEYS) {
            keys.add(keybind);
        }
    }

    /** Same shape as CLI {@code LocalizationManager#translate("item." + id, "block." + id)}. */
    /**
     * Registry / tag keys for the {@code emi/} bundle (no Patchouli book or site UI strings).
     */
    public static Set<String> collectEmiRegistry(
            Set<String> items, Set<String> fluids, Set<String> tags) {
        Set<String> keys = new TreeSet<>();
        if (items != null) {
            for (String id : items) {
                addRegistryKeys(keys, id, "item");
            }
        }
        if (fluids != null) {
            for (String id : fluids) {
                addRegistryKeys(keys, id, "fluid");
            }
        }
        if (tags != null) {
            for (String tag : tags) {
                addTagKeys(keys, tag);
            }
        }
        return keys;
    }

    private static void addTagKeys(Set<String> keys, String tag) {
        if (tag == null || tag.isBlank()) {
            return;
        }
        String dotted = tag.replace('/', '.').replace(':', '.');
        keys.add("tag.item." + dotted);
    }

    private static void addRegistryKeys(Set<String> keys, String registryId, String kind) {
        String normalized = normalizeRegistryId(registryId);
        if (normalized == null || normalized.isBlank() || normalized.startsWith("#")) {
            return;
        }
        String dotted = normalized.replace('/', '.').replace(':', '.');
        keys.add(kind + "." + dotted);
        if ("item".equals(kind)) {
            keys.add("block." + dotted);
        } else if ("block".equals(kind)) {
            keys.add("item." + dotted);
        } else if ("fluid".equals(kind)) {
            keys.add("fluid." + dotted);
            keys.add("item." + dotted);
            keys.add("block." + dotted);
        }
    }

    /** Plain registry id: strips legacy {@code item:} prefix, SNBT, and {@code @nbtHash} suffix. */
    private static String normalizeRegistryId(String registryId) {
        if (registryId == null) {
            return null;
        }
        String id = registryId.trim();
        if (id.startsWith("item:")) {
            id = id.substring(5);
        }
        int brace = id.indexOf('{');
        if (brace >= 0) {
            id = id.substring(0, brace);
        }
        int at = id.indexOf('@');
        if (at >= 0) {
            id = id.substring(0, at);
        }
        return id;
    }

    private static void collectFromBook(Book book, Set<String> keys) {
        collectPatchouliText(book.getName(), keys);
        collectPatchouliText(book.getLandingText(), keys);
        collectPatchouliText(book.getSubtitle(), keys);
        for (BookCategory cat : book.getCategories()) {
            collectPatchouliText(cat.getName(), keys);
            collectPatchouliText(cat.getDescription(), keys);
        }
        for (BookEntry entry : book.getEntries()) {
            collectPatchouliText(entry.getName(), keys);
            for (BookPage page : entry.getPages()) {
                if (page.getRaw() != null) {
                    collectFromJson(page.getRaw(), keys);
                }
            }
        }
    }

    private static void collectFromRecipe(JsonElement root, Set<String> keys) {
        if (root == null) {
            return;
        }
        walkRecipeJson(root, keys);
    }

    private static void walkRecipeJson(JsonElement el, Set<String> keys) {
        if (el == null || el.isJsonNull()) {
            return;
        }
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            return;
        }
        if (el.isJsonArray()) {
            for (JsonElement child : el.getAsJsonArray()) {
                walkRecipeJson(child, keys);
            }
            return;
        }
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("type") && obj.get("type").isJsonPrimitive()) {
                String type = obj.get("type").getAsString();
                if (type.contains(":")) {
                    keys.add("recipe." + type.replace(':', '.'));
                }
            }
            if (obj.has("category") && obj.get("category").isJsonPrimitive()) {
                String cat = obj.get("category").getAsString();
                if (cat.contains(":")) {
                    keys.add("gtceu." + cat.replace(':', '.'));
                    keys.add("recipe." + cat.replace(':', '.'));
                }
            }
            for (var entry : obj.entrySet()) {
                walkRecipeJson(entry.getValue(), keys);
            }
        }
    }

    private static void collectFromJson(JsonObject obj, Set<String> keys) {
        for (var entry : obj.entrySet()) {
            JsonElement value = entry.getValue();
            if (value instanceof JsonPrimitive prim && prim.isString()) {
                collectPatchouliText(prim.getAsString(), keys);
            } else if (value instanceof JsonObject jsonObj) {
                collectFromJson(jsonObj, keys);
            } else if (value instanceof JsonArray arr) {
                for (JsonElement child : arr) {
                    if (child instanceof JsonPrimitive p && p.isString()) {
                        collectPatchouliText(p.getAsString(), keys);
                    } else if (child instanceof JsonObject childObj) {
                        collectFromJson(childObj, keys);
                    }
                }
            }
        }
    }

    private static void collectPatchouliText(String text, Set<String> keys) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher matcher = PATCHOULI_MACRO.matcher(text);
        while (matcher.find()) {
            String code = matcher.group(1);
            if (code.startsWith("k:")) {
                String bind = code.substring(2);
                keys.add(bind);
                keys.add("key." + bind);
                keys.add("key.keyboard." + bind);
            }
        }
    }
}
