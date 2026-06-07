package team.terrafirmgreg.fieldguide.render;

import lombok.extern.slf4j.Slf4j;
import team.terrafirmgreg.fieldguide.localization.LocalizationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Patchouli $(...) markup → HTML. Structural tags (p/br/li) use {@link #root};
 * inline tags (bold/link/color) use {@link #inlineStack} only — never mix the two
 * (see Patchouli {@code BookTextParser} / {@code SpanState}).
 */
@Slf4j
public class TextFormatter {
    private static final Map<String, String> VANILLA_COLORS = Map.ofEntries(
        Map.entry("0", "#000000"),
        Map.entry("1", "#0000AA"),
        Map.entry("2", "#00AA00"),
        Map.entry("3", "#00AAAA"),
        Map.entry("4", "#AA0000"),
        Map.entry("5", "#AA00AA"),
        Map.entry("6", "#FFAA00"),
        Map.entry("7", "#AAAAAA"),
        Map.entry("8", "#555555"),
        Map.entry("9", "#5555FF"),
        Map.entry("a", "#55FF55"),
        Map.entry("b", "#55FFFF"),
        Map.entry("c", "#FF5555"),
        Map.entry("d", "#FF55FF"),
        Map.entry("e", "#FFFF55"),
        Map.entry("f", "#FFFFFF")
    );

    /** Patchouli {@code Book.DEFAULT_MACROS}. */
    private static final Map<String, String> DEFAULT_MACROS = Map.of(
        "$(list", "$(li",
        "/$", "$()",
        "<br>", "$(br)",
        "$(item)", "$(#b0b)",
        "$(thing)", "$(#490)"
    );

    private static final Map<String, Map<String, String>> ROOT_TAGS = createRootTags();

    private static Map<String, Map<String, String>> createRootTags() {
        Map<String, Map<String, String>> rootTags = new HashMap<>();

        Map<String, String> pMap = new HashMap<>();
        pMap.put("", "</p>\n");
        pMap.put("p", "<br/>\n");
        pMap.put("ol", "</p>\n<ol>\n\t<li>");
        for (int i = 1; i <= 9; i++) {
            pMap.put(listRootKey(i), pToList(i));
        }
        rootTags.put("p", Map.copyOf(pMap));

        rootTags.put("ol", Map.of(
            "", "</li>\n</ol>\n",
            "ol", "</li>\n\t<li>",
            "p", "</li>\n</ol><p>"
        ));

        Map<String, String> liCommon = new HashMap<>();
        liCommon.put("", "</li>\n</ul>\n");
        liCommon.put("p", "</li>\n</ul><p>");

        for (int i = 1; i <= 9; i++) {
            String liKey = listRootKey(i);
            Map<String, String> liMap = new HashMap<>(liCommon);
            for (int j = 1; j <= 9; j++) {
                liMap.put(listRootKey(j), liToLi(j));
            }
            rootTags.put(liKey, liMap);
        }

        return rootTags;
    }

    private static String listRootKey(int level) {
        return level == 1 ? "li" : "li" + level;
    }

    private static String listItemOpen(int level) {
        if (level <= 1) {
            return "<li>";
        }
        return "<li class=\"patchouli-li-" + level + "\">";
    }

    private static String pToList(int level) {
        return "</p>\n<ul>\n\t" + listItemOpen(level);
    }

    private static String liToLi(int level) {
        return "</li>\n\t" + listItemOpen(level);
    }

    private static final Pattern SEARCH_STRIP_PATTERN = Pattern.compile("\\$\\([^)]*\\)");
    private static final Pattern FORMATTING_PATTERN = Pattern.compile("(\\$\\(([^)]*)\\))|§(.)");
    private static final Pattern OL_PATTERN = Pattern.compile("\\$\\\\(br\\\\) {2}[0-9+]. ");

    private final List<String> buffer;
    private final Map<String, String> keybindings;
    private String root;
    /** Inline closers only — never structural tags like {@code </li>}. */
    private final List<String> inlineStack;
    private final LocalizationManager localizationManager;
    private boolean externalLinkOpen;

    public TextFormatter(
            List<String> buffer,
            String text,
            LocalizationManager localizationManager,
            Map<String, String> bookMacros) {
        this.buffer = buffer;
        this.localizationManager = localizationManager;
        this.keybindings = localizationManager != null ? localizationManager.getKeybindings() : new HashMap<>();
        this.root = "p";
        this.inlineStack = new ArrayList<>();

        this.buffer.add("<p>");
        processText(expandMacros(text, bookMacros));
    }

    public static Map<String, String> mergeMacros(Map<String, String> bookMacros) {
        Map<String, String> merged = new LinkedHashMap<>(DEFAULT_MACROS);
        if (bookMacros != null) {
            merged.putAll(bookMacros);
        }
        return merged;
    }

    /** Patchouli {@code BookTextParser.expandMacros}. */
    public static String expandMacros(String text, Map<String, String> bookMacros) {
        if (text == null) {
            return "";
        }
        Map<String, String> macros = mergeMacros(bookMacros);
        String actualText = text;
        for (int i = 0; i < 10; i++) {
            String newText = actualText;
            for (Map.Entry<String, String> entry : macros.entrySet()) {
                newText = newText.replace(entry.getKey(), entry.getValue());
            }
            if (newText.equals(actualText)) {
                break;
            }
            actualText = newText;
        }
        return actualText;
    }

    public static String searchStrip(String input) {
        return SEARCH_STRIP_PATTERN.matcher(input).replaceAll("");
    }

    public static String stripVanillaFormatting(String text) {
        return text.replaceAll("§.", "");
    }

    public static void formatText(List<String> buffer, String text, LocalizationManager localizationManager) {
        formatText(buffer, text, localizationManager, null);
    }

    public static void formatText(
            List<String> buffer,
            String text,
            LocalizationManager localizationManager,
            Map<String, String> bookMacros) {
        new TextFormatter(buffer, text, localizationManager, bookMacros);
    }

    private void processText(String text) {
        text = OL_PATTERN.matcher(text).replaceAll("$(ol)");

        int cursor = 0;
        Matcher matcher = FORMATTING_PATTERN.matcher(text);

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            String key = matcher.group(2);
            if (key == null) {
                key = matcher.group(3);
            }

            if (start > cursor) {
                buffer.add(text.substring(cursor, start));
            }

            processFormattingKey(key, text, end);

            cursor = end;
        }

        if (cursor < text.length()) {
            buffer.add(text.substring(cursor));
        }

        flushInlineStack();
        updateRoot("");
    }

    private void processFormattingKey(String key, String text, int end) {
        if (key.isEmpty() || key.equals("reset") || key.equals("clear")) {
            resetInlineFormatting();
        } else if (key.equals("bold") || key.equals("l")) {
            matchingTags("<strong>", "</strong>");
        } else if (key.equals("italic") || key.equals("italics") || key.equals("o")) {
            matchingTags("<em>", "</em>");
        } else if (key.equals("underline") || key.equals("n")) {
            matchingTags("<u>", "</u>");
        } else if (key.equals("strike") || key.equals("m")) {
            matchingTags("<del>", "</del>");
        } else if (key.equals("k") || key.equals("obf")) {
            matchingTags("<span class=\"patchouli-obf\">", "</span>");
        } else if (key.equals("nocolor")) {
            closeColorSpan();
        } else if (key.equals("br")) {
            lineBreak(1);
        } else if (key.equals("br2") || key.equals("2br") || key.equals("p")) {
            lineBreak(2);
        } else if (key.equals("ol")) {
            updateRoot("ol");
        } else if (ListHelper.isListFormat(key)) {
            flushInlineStack();
            updateRoot(listRootKey(ListHelper.extractListLevel(key)));
        } else if (key.startsWith("l:http")) {
            externalLinkOpen = true;
            matchingTags("<a href=\"" + escapeAttr(key.substring(2)) + "\">", "</a>");
        } else if (key.startsWith("l:")) {
            String link = key.substring(2);
            if (link.contains(":")) {
                link = link.substring(link.indexOf(':') + 1);
            }
            link = link.contains("#") ? link.replace("#", ".html#") : link + ".html";
            matchingTags("<a href=\"../" + escapeAttr(link) + "\">", "</a>");
        } else if (key.equals("/l")) {
            closeLink();
        } else if (key.startsWith("t:") || key.startsWith("tooltip:")) {
            openTooltip(key.substring(key.indexOf(':') + 1));
        } else if (key.equals("/t")) {
            closeTooltip();
        } else if (key.startsWith("c:") || key.startsWith("command:")) {
            openCommand(key.substring(key.indexOf(':') + 1));
        } else if (key.equals("/c")) {
            closeCommand();
        } else if (key.equals("playername")) {
            buffer.add("<span class=\"patchouli-playername\">Player</span>");
        } else if (key.startsWith("#")) {
            colorTags(normalizeHexColor(key));
        } else if (key.equals("d")) {
            String nextText = text.substring(end, Math.min(end + 20, text.length())).toLowerCase();
            if (nextText.contains("white") || nextText.contains("brilliant")) {
                matchingTags("<span class=\"minecraft-white\">", "</span>");
            }
        } else if (VANILLA_COLORS.containsKey(key)) {
            colorTags(VANILLA_COLORS.get(key));
        } else if (key.startsWith("k:")) {
            appendKeybind(key.substring(2));
        } else {
            log.debug("Unrecognized Formatting Code $({}), leaving literal", key);
            buffer.add("$(" + key + ")");
        }
    }

    private void resetInlineFormatting() {
        boolean showExternal = externalLinkOpen;
        flushInlineStack();
        if (showExternal) {
            appendExternalLinkMarker();
        }
    }

    private void lineBreak(int count) {
        if (inListRoot()) {
            for (int i = 0; i < count; i++) {
                buffer.add("<br/>\n");
            }
            return;
        }
        for (int i = 0; i < count; i++) {
            updateRoot("p");
        }
    }

    private boolean inListRoot() {
        return root.startsWith("li");
    }

    private void appendKeybind(String keybindKey) {
        if (keybindings.containsKey(keybindKey)) {
            buffer.add(keybindings.get(keybindKey));
        } else if (localizationManager != null) {
            String translated = localizationManager.translate(keybindKey);
            if (translated != null && !translated.equals(keybindKey)) {
                buffer.add("<span class=\"keybind\" style=\"color:#333;\">");
                buffer.add(translated);
                buffer.add("</span>");
            } else {
                buffer.add("<span class=\"keybind\" style=\"color:#888;font-style:italic;\" title=\"");
                buffer.add(escapeAttr(keybindKey));
                buffer.add("\">");
                buffer.add("未配置");
                buffer.add("</span>");
            }
        } else {
            buffer.add("<span class=\"keybind\" style=\"color:#666;font-style:italic;\" title=\"");
            buffer.add(escapeAttr(keybindKey));
            buffer.add("\">");
            buffer.add(keybindKey);
            buffer.add("</span>");
        }
    }

    private void openTooltip(String tooltipText) {
        matchingTags("<abbr class=\"patchouli-tooltip\" title=\"" + escapeAttr(tooltipText) + "\">", "</abbr>");
    }

    private void closeTooltip() {
        closeInlineTag("</abbr>");
    }

    private void openCommand(String command) {
        String title = command.startsWith("/")
                ? (command.length() < 20 ? command : command.substring(0, 20) + "...")
                : "INVALID COMMAND (must begin with /)";
        matchingTags("<code class=\"patchouli-command\" title=\"" + escapeAttr(title) + "\">", "</code>");
    }

    private void closeCommand() {
        closeInlineTag("</code>");
    }

    private void matchingTags(String start, String end) {
        buffer.add(start);
        inlineStack.add(end);
    }

    private void colorTags(String color) {
        matchingTags("<span style=\"color:" + color + ";\">", "</span>");
    }

    static String normalizeHexColor(String key) {
        if (!key.startsWith("#")) {
            return key;
        }
        String parse = key.substring(1);
        if (parse.length() == 3) {
            parse = "" + parse.charAt(0) + parse.charAt(0)
                    + parse.charAt(1) + parse.charAt(1)
                    + parse.charAt(2) + parse.charAt(2);
            return "#" + parse;
        }
        if (parse.length() == 6) {
            return key;
        }
        return key;
    }

    /** Close inline tags only (Patchouli reset / end of span cluster). */
    private void flushInlineStack() {
        for (int i = inlineStack.size() - 1; i >= 0; i--) {
            buffer.add(inlineStack.get(i));
        }
        inlineStack.clear();
    }

    /** Patchouli $(/l): end link without closing other inline tags. */
    private void closeLink() {
        if (closeInlineTag("</a>") && externalLinkOpen) {
            appendExternalLinkMarker();
        }
    }

    private void closeColorSpan() {
        closeInlineTag("</span>");
    }

    private void appendExternalLinkMarker() {
        buffer.add("<span class=\"patchouli-external-link\" aria-hidden=\"true\">\u21AA</span>");
        externalLinkOpen = false;
    }

    /** Remove and emit a single inline closer from the stack. */
    private boolean closeInlineTag(String closer) {
        for (int i = inlineStack.size() - 1; i >= 0; i--) {
            if (closer.equals(inlineStack.get(i))) {
                buffer.add(inlineStack.remove(i));
                return true;
            }
        }
        return false;
    }

    private void updateRoot(String newRoot) {
        Map<String, String> rootMap = ROOT_TAGS.get(root);
        if (rootMap != null && rootMap.containsKey(newRoot)) {
            buffer.add(rootMap.get(newRoot));
            root = newRoot;
        }
    }

    private static String escapeAttr(String value) {
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;");
    }
}
