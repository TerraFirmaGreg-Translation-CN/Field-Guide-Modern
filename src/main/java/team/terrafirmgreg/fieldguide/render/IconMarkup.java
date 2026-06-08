package team.terrafirmgreg.fieldguide.render;

import team.terrafirmgreg.fieldguide.asset.ItemImageResult;
import team.terrafirmgreg.fieldguide.export.IconRef;

/**
 * HTML for field-guide {@code field-guide-icon-atlas} sprites (CSS under {@code assets/icons/}).
 */
public final class IconMarkup {

    /** From {@code en_us/<category>/<entry>.html} to site root. */
    public static final String ASSET_ROOT = "../../";

    private static final int CAROUSEL_INTERVAL_MS = 800;

    private IconMarkup() {}

    public static String inline(ItemImageResult icon) {
        return img(icon, null);
    }

    public static String img(ItemImageResult icon, String extraClass) {
        return img(icon, extraClass, ASSET_ROOT);
    }

    /**
     * @param assetRoot path from the HTML file to the site root (e.g. {@code ../} on category pages,
     *                  {@code ../../} on entry pages)
     */
    public static String img(ItemImageResult icon, String extraClass, String assetRoot) {
        if (icon == null) {
            return "";
        }
        if (icon.isEmiTag()) {
            String title = icon.getName() != null ? icon.getName() : icon.getEmiTagId();
            String classes = "emi-slot emi-handbook-tag";
            if (extraClass != null && !extraClass.isEmpty()) {
                classes += " " + extraClass;
            }
            String slot = slotClass(scaleContext(extraClass));
            String inner = String.format(
                    "<span class=\"%s\" data-tag-id=\"%s\" title=\"%s\"></span>",
                    classes,
                    escapeAttr(icon.getEmiTagId()),
                    escapeAttr(title));
            if (!slot.isEmpty()) {
                return "<span class=\"" + slot + "\">" + inner + "</span>";
            }
            return inner;
        }
        if (icon.isAtlas()) {
            String html = atlasHtml(icon, extraClass);
            if (icon.hasTagClickId()) {
                return wrapTagClick(icon, html);
            }
            return html;
        }
        String classes = extraClass != null && !extraClass.isEmpty() ? extraClass : "";
        String classAttr = classes.isEmpty() ? "" : " class=\"" + classes + "\"";
        return String.format("<img%s src=\"%s%s\" alt=\"\" />", classAttr, assetRoot, icon.getPath());
    }

    private static String wrapTagClick(ItemImageResult icon, String inner) {
        return String.format(
                "<span class=\"handbook-tag-click\" data-tag-id=\"%s\" role=\"button\" tabindex=\"0\" title=\"%s\">%s</span>",
                escapeAttr(icon.getEmiTagId()),
                escapeAttr(icon.getName() != null ? icon.getName() : icon.getEmiTagId()),
                inner);
    }

    private static String atlasHtml(ItemImageResult icon, String extraClass) {
        String scaleContext = scaleContext(extraClass);
        if (icon.isCarousel()) {
            return carouselHtml(icon.getAtlasIcons(), extraClass, scaleContext);
        }
        return singleAtlasSpan(icon.primaryAtlas(), extraClass, scaleContext, false);
    }

    private static String carouselHtml(java.util.List<IconRef> refs, String extraClass, String scaleContext) {
        StringBuilder sb = new StringBuilder("<div class=\"icon-carousel");
        if (extraClass != null && !extraClass.isEmpty()) {
            sb.append(' ').append(extraClass);
        }
        sb.append("\" data-carousel-interval=\"").append(CAROUSEL_INTERVAL_MS).append("\">");
        for (int i = 0; i < refs.size(); i++) {
            sb.append(singleAtlasSpan(refs.get(i), null, scaleContext, i == 0));
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String singleAtlasSpan(
            IconRef ref, String extraClass, String scaleContext, boolean carouselActive) {
        StringBuilder cls = new StringBuilder(ref.cssClass());
        String slot = slotClass(scaleContext);
        if (!slot.isEmpty()) {
            cls.append(' ').append(slot);
        }
        if (extraClass != null && !extraClass.isEmpty()) {
            cls.append(' ').append(extraClass);
        }
        if (carouselActive) {
            cls.append(" icon-carousel-active");
        }
        return String.format(
                "<span class=\"%s\" %s=\"%s\"></span>",
                cls,
                ref.dataAttribute(),
                escapeAttr(ref.registryId()));
    }

    private static String slotClass(String scaleContext) {
        return switch (scaleContext) {
            case "title" -> "icon-slot-48";
            case "card" -> "icon-slot-32";
            default -> "";
        };
    }

    private static String scaleContext(String extraClass) {
        if (extraClass == null) {
            return "";
        }
        if (extraClass.contains("icon-title")) {
            return "title";
        }
        if (extraClass.contains("item-header-icon") || extraClass.contains("entry-card-icon")) {
            return "card";
        }
        return "";
    }

    private static String escapeAttr(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;");
    }
}
