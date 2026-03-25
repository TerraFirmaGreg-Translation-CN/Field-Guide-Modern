package team.terrafirmgreg.fieldguide.render;

import lombok.extern.slf4j.Slf4j;
import team.terrafirmgreg.fieldguide.localization.LocalizationManager;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Map<String, Map<String, String>> ROOT_TAGS = createRootTags();

    private static Map<String, Map<String, String>> createRootTags() {
        Map<String, Map<String, String>> rootTags = new HashMap<>();

        // Paragraph tags
        rootTags.put("p", Map.of(
            "", "</p>\n",
            "p", "<br/>\n",
            "li", "</p>\n<ul>\n\t<li>",
            "ol", "</p>\n<ol>\n\t<li>"
        ));

        // Ordered list tags
        rootTags.put("ol", Map.of(
            "", "</li>\n</ol>\n",
            "ol", "</li>\n\t<li>",
            "p", "</li>\n</ol><p>"
        ));

        // List item tags - common patterns for all li levels
        Map<String, String> liCommon = new HashMap<>();
        liCommon.put("", "</li>\n</ul>\n");
        liCommon.put("p", "</li>\n</ul><p>");

        // Add li transitions for levels 1-9
        for (int i = 1; i <= 9; i++) {
            String liKey = i == 1 ? "li" : "li" + i;

            Map<String, String> liMap = new HashMap<>(liCommon);
            // Add transitions to all other li levels
            for (int j = 1; j <= 9; j++) {
                String targetKey = j == 1 ? "li" : "li" + j;
                liMap.put(targetKey, "</li>\n\t<li>");
            }

            rootTags.put(liKey, liMap);
        }

        return rootTags;
    }

    private static final Pattern SEARCH_STRIP_PATTERN = Pattern.compile("\\$\\([^)]*\\)");

    private static final Pattern FORMATTING_PATTERN = Pattern.compile("(\\$\\(([^)]*)\\))|§(.)");
    private static final Pattern OL_PATTERN = Pattern.compile("\\$\\\\(br\\\\) {2}[0-9+]. ");

    private final List<String> buffer;
    private final Map<String, String> keybindings;
    private String root;
    private final Stack<String> stack;
    private LocalizationManager localizationManager;

    public TextFormatter(List<String> buffer, String text, LocalizationManager localizationManager) {
        this.buffer = buffer;
        this.localizationManager = localizationManager;
        // 从LocalizationManager获取keybindings
        this.keybindings = localizationManager != null ? localizationManager.getKeybindings() : new HashMap<>();
        this.root = "p";
        this.stack = new Stack<>();

        this.buffer.add("<p>");
        processText(text);
    }

    public static String searchStrip(String input) {
        return SEARCH_STRIP_PATTERN.matcher(input).replaceAll("");
    }

    public static String stripVanillaFormatting(String text) {
        return text.replaceAll("§.", "");
    }

    public static void formatText(List<String> buffer, String text, LocalizationManager localizationManager) {
        new TextFormatter(buffer, text, localizationManager);
    }

    private void processText(String text) {
        // Patchy doesn't have an ordered list function / macro. So we have to recognize 
        // a specific pattern outside of a macro to properly HTML-ify them
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

            // Append text before the match
            if (start > cursor) {
                buffer.add(text.substring(cursor, start));
            }

            processFormattingKey(key, text, end);

            cursor = end;
        }

        // Append remaining text
        if (cursor < text.length()) {
            buffer.add(text.substring(cursor));
        }

        flushStack();
        updateRoot("");
    }

    private void processFormattingKey(String key, String text, int end) {
        if (key.isEmpty()) {
            flushStack();
        } else if (key.equals("bold") || key.equals("l")) {
            matchingTags("<strong>", "</strong>");
        } else if (key.equals("italic") || key.equals("italics") || key.equals("o")) {
            matchingTags("<em>", "</em>");
        } else if (key.equals("underline")) {
            matchingTags("<u>", "</u>");
        } else if (key.equals("br")) {
            updateRoot("p");
        } else if (key.equals("br2") || key.equals("2br")) {
            updateRoot("p");
            updateRoot("p");
        } else if (key.equals("ol")) {  // Fake formatting code
            updateRoot("ol");
        } else if (key.equals("li") || key.matches("li\\d")) {
            // 支持li, li2, li3等格式化代码
            int level = ListHelper.extractListLevel(key);

            // 生成列表项的开始标签
            buffer.add(ListHelper.generateListItemStart(level));

            // 推送结束标签到栈中
            stack.push("</li>");
        } else if (key.startsWith("l:http")) {
            matchingTags("<a href=\"" + key.substring(2) + "\">", "</a>");
        } else if (key.startsWith("l:")) {
            String link = key.substring(2);
            if (link.contains(":")) {
                // Links from addons will have a namespace, but the namespace isn't relevant.
                link = link.substring(link.indexOf(':') + 1);
            }
            link = link.contains("#") ? link.replace("#", ".html#") : link + ".html";
            matchingTags("<a href=\"../" + link + "\">", "</a>");
        } else if (key.equals("/l")) {
            // End Link, ends the current link but maintains formatting ($() also ends links)
            flushStack();
        } else if (key.equals("thing")) {
            colorTags("#3E8A00");  // Patchy uses #490, we darken it due to accessibility/contrast reasons
        } else if (key.equals("item")) {
            colorTags("#b0b");
        } else if (key.startsWith("#")) {
            colorTags(key);
        } else if (key.equals("d")) {
            String nextText = text.substring(end, Math.min(end + 20, text.length())).toLowerCase();
            if (nextText.contains("white") || nextText.contains("brilliant")) {
                // We use this color instead of white for temperature tooltips. Use custom CSS for white.
                matchingTags("<span class=\"minecraft-white\">", "</span>");
            }
        } else if (VANILLA_COLORS.containsKey(key)) {
            colorTags(VANILLA_COLORS.get(key));
        } else if (key.startsWith("k:")) {
            String keybindKey = key.substring(2);

            // 根据Patchouli的实现，先检查是否有实际的按键绑定
            if (keybindings.containsKey(keybindKey)) {
                buffer.add(keybindings.get(keybindKey));
            } else if (localizationManager != null) {
                // 尝试从语言文件中翻译按键绑定
                String translated = localizationManager.translate(keybindKey);

                // Patchouli的实现中，如果没有找到按键，会显示"N/A"
                // 但对于网页版，我们显示翻译后的文本更合适
                if (translated != null && !translated.equals(keybindKey)) {
                    buffer.add("<span class=\"keybind\" style=\"color:#333;\">");
                    buffer.add(translated);
                    buffer.add("</span>");
                } else {
                    // 翻译失败，按照Patchouli的风格显示"未配置"
                    buffer.add("<span class=\"keybind\" style=\"color:#888;font-style:italic;\" title=\"");
                    buffer.add(keybindKey);
                    buffer.add("\">");
                    buffer.add("未配置");
                    buffer.add("</span>");
                }
            } else {
                // 最坏情况，显示原始键
                buffer.add("<span class=\"keybind\" style=\"color:#666;font-style:italic;\" title=\"");
                buffer.add(keybindKey);
                buffer.add("\">");
                buffer.add(keybindKey);
                buffer.add("</span>");
            }
        } else if (key.startsWith("t:")) {
            String tooltips = key.substring(2);
            // FIXME matchingTags("", "<span style=\"color:#888;font-style:italic;\">" + tooltips + "</span>");
        } else if (key.equals("/t")) {
            // FIXME flushStack();
        } else {
            log.warn("Unrecognized Formatting Code $({})", key);
        }
    }

    private void matchingTags(String start, String end) {
        buffer.add(start);
        stack.push(end);
    }

    private void colorTags(String color) {
        matchingTags("<span style=\"color:" + color + ";\">", "</span>");
    }

    private void flushStack() {
        // Reverse iterate through stack to close tags in correct order
        for (int i = stack.size() - 1; i >= 0; i--) {
            buffer.add(stack.get(i));
        }
        stack.clear();
    }

    private void updateRoot(String newRoot) {
        Map<String, String> rootMap = ROOT_TAGS.get(root);
        if (rootMap != null && rootMap.containsKey(newRoot)) {
            buffer.add(rootMap.get(newRoot));
            root = newRoot;
        }
    }
}