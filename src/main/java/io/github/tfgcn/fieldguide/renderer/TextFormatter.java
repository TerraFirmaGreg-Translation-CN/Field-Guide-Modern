package io.github.tfgcn.fieldguide.renderer;

import lombok.extern.slf4j.Slf4j;

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

    private static final Map<String, Map<String, String>> ROOT_TAGS = Map.of(
        "p", Map.of(
            "", "</p>\n",
            "p", "<br/>\n",
            "li", "</p>\n<ul>\n\t<li>",
            "ol", "</p>\n<ol>\n\t<li>"
        ),
        "li", Map.of(
            "", "</li>\n</ul>\n",
            "li", "</li>\n\t<li>",
            "p", "</li>\n</ul><p>"
        ),
        "ol", Map.of(
            "", "</li>\n</ol>\n",
            "ol", "</li>\n\t<li>",
            "p", "</li>\n</ol><p>"
        )
    );

    private static final Pattern FORMATTING_PATTERN = Pattern.compile("(\\$\\(([^)]*)\\))|§(.)");
    private static final Pattern OL_PATTERN = Pattern.compile("\\$\\\\(br\\\\) {2}[0-9+]. ");

    private final List<String> buffer;
    private final Map<String, String> keybindings;
    private String root;
    private final Stack<String> stack;

    public TextFormatter(List<String> buffer, String text, Map<String, String> keybindings) {
        this.buffer = buffer;
        this.keybindings = keybindings;
        this.root = "p";
        this.stack = new Stack<>();
        
        this.buffer.add("<p>");
        processText(text);
    }

    public static void formatText(List<String> buffer, String text, Map<String, String> keybindings) {
        new TextFormatter(buffer, text, keybindings);
    }

    public static String stripVanillaFormatting(String text) {
        return text.replaceAll("§.", "");
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
        } else if (key.equals("li")) {
            updateRoot("li");
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
        } else if (key.startsWith("k:") && keybindings.containsKey(key.substring(2))) {
            buffer.add(keybindings.get(key.substring(2)));
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