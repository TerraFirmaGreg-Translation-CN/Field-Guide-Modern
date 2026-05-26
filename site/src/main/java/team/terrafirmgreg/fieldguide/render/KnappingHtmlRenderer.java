package team.terrafirmgreg.fieldguide.render;

import java.util.List;

/**
 * Renders knapping patterns as HTML/CSS when export lacks {@code textures/gui/knapping/*} tiles.
 */
public final class KnappingHtmlRenderer {

    private KnappingHtmlRenderer() {}

    public static String renderOverlay(List<String> pattern, boolean outsideSlotRequired, String styleKey) {
        int offsetY = (5 - pattern.size()) / 2;
        int offsetX = (5 - pattern.get(0).length()) / 2;
        StringBuilder cells = new StringBuilder();
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                boolean inside =
                        0 <= y - offsetY
                                && y - offsetY < pattern.size()
                                && 0 <= x - offsetX
                                && x - offsetX < pattern.get(y - offsetY).length();
                boolean dark;
                if (inside) {
                    char c = pattern.get(y - offsetY).charAt(x - offsetX);
                    dark = c != ' ';
                } else {
                    dark = outsideSlotRequired;
                }
                String tone = dark ? "low" : "hi";
                cells.append(
                        String.format(
                                "<div class=\"knapping-cell knapping-%s-%s\"></div>",
                                styleKey, tone));
            }
        }
        return "<div class=\"knapping-grid\">" + cells + "</div>";
    }

    public static String styleKeyFromKnappingType(String knappingType120, String type118) {
        if (knappingType120 != null && knappingType120.startsWith("tfc:")) {
            return knappingType120.substring(4);
        }
        if (type118 != null && type118.startsWith("tfc:") && type118.endsWith("_knapping")) {
            return type118.substring(4, type118.length() - "_knapping".length());
        }
        return "rock";
    }
}
