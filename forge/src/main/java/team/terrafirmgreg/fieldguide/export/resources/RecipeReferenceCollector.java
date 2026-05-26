package team.terrafirmgreg.fieldguide.export.resources;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Set;
import java.util.TreeSet;

/** Shallow walk of recipe JSON for registry ids used by closure export. */
final class RecipeReferenceCollector {

    private RecipeReferenceCollector() {}

    record References(Set<String> items, Set<String> blocks, Set<String> fluids, Set<String> tags) {}

    static References collectAll(JsonElement root) {
        Set<String> items = new TreeSet<>();
        Set<String> blocks = new TreeSet<>();
        Set<String> fluids = new TreeSet<>();
        Set<String> tags = new TreeSet<>();
        walk(root, items, blocks, fluids, tags);
        return new References(items, blocks, fluids, tags);
    }

    static Set<String> collectItems(JsonElement root) {
        return collectAll(root).items();
    }

    static Set<String> collectNamespaces(JsonElement root) {
        Set<String> out = new TreeSet<>();
        walkNamespaces(root, out);
        return out;
    }

    private static void walkNamespaces(JsonElement el, Set<String> out) {
        if (el == null || el.isJsonNull()) {
            return;
        }
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            maybeAddNamespace(el.getAsString(), out);
            return;
        }
        if (el.isJsonArray()) {
            for (JsonElement child : el.getAsJsonArray()) {
                walkNamespaces(child, out);
            }
            return;
        }
        if (el.isJsonObject()) {
            for (var entry : el.getAsJsonObject().entrySet()) {
                walkNamespaces(entry.getValue(), out);
            }
        }
    }

    private static void walk(
            JsonElement el,
            Set<String> items,
            Set<String> blocks,
            Set<String> fluids,
            Set<String> tags) {
        if (el == null || el.isJsonNull()) {
            return;
        }
        if (el.isJsonPrimitive()) {
            if (el.getAsJsonPrimitive().isString()) {
                classifyString(el.getAsString(), null, items, blocks, fluids, tags);
            }
            return;
        }
        if (el.isJsonArray()) {
            for (JsonElement child : el.getAsJsonArray()) {
                walk(child, items, blocks, fluids, tags);
            }
            return;
        }
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            for (var entry : obj.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    classifyString(value.getAsString(), key, items, blocks, fluids, tags);
                } else {
                    walk(value, items, blocks, fluids, tags);
                }
            }
        }
    }

    private static void classifyString(
            String raw,
            String key,
            Set<String> items,
            Set<String> blocks,
            Set<String> fluids,
            Set<String> tags) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        if (raw.startsWith("#")) {
            String tag = TagClosureExpander.normalizeTagRef(raw);
            if (tag != null) {
                tags.add(tag);
            }
            return;
        }
        if (key != null) {
            if ("tag".equals(key) || key.endsWith("_tag") || key.endsWith("Tag")) {
                String tag = TagClosureExpander.normalizeTagRef(raw);
                if (tag != null) {
                    tags.add(tag);
                }
                return;
            }
            if (isFluidKey(key)) {
                addRegistryId(raw, fluids);
                return;
            }
            if (isBlockKey(key)) {
                addRegistryId(raw, blocks);
                return;
            }
            if (isItemKey(key)) {
                addRegistryId(raw, items);
                return;
            }
        }
        if (raw.contains(":")) {
            addRegistryId(raw, items);
        }
    }

    private static boolean isItemKey(String key) {
        return switch (key) {
            case "item", "base", "result", "output" -> true;
            default -> key.endsWith("_item") || key.endsWith("Item");
        };
    }

    private static boolean isBlockKey(String key) {
        return "block".equals(key) || key.endsWith("_block") || key.endsWith("Block");
    }

    private static boolean isFluidKey(String key) {
        return "fluid".equals(key) || key.contains("fluid") || key.contains("Fluid");
    }

    private static void addRegistryId(String raw, Set<String> out) {
        int bracket = raw.indexOf('[');
        if (bracket > 0) {
            raw = raw.substring(0, bracket);
        }
        int hash = raw.indexOf('#');
        int brace = raw.indexOf('{');
        int cut = raw.length();
        if (hash >= 0) cut = Math.min(cut, hash);
        if (brace >= 0) cut = Math.min(cut, brace);
        raw = raw.substring(0, cut).trim();
        if (raw.indexOf(':') > 0) {
            out.add(raw);
        }
    }

    private static void maybeAddNamespace(String raw, Set<String> out) {
        if (raw == null || raw.startsWith("#")) {
            return;
        }
        int colon = raw.indexOf(':');
        if (colon > 0) {
            out.add(raw.substring(0, colon));
        }
    }
}
