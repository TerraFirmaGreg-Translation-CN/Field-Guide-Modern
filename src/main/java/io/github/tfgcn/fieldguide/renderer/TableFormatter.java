package io.github.tfgcn.fieldguide.renderer;

import io.github.tfgcn.fieldguide.Context;
import io.github.tfgcn.fieldguide.patchouli.page.tfc.PageTable;
import io.github.tfgcn.fieldguide.patchouli.page.tfc.PageTableLegend;
import io.github.tfgcn.fieldguide.patchouli.page.tfc.PageTableString;

import java.util.List;
import java.util.Map;

public class TableFormatter {

    public static void formatTable(Context context, List<String> buffer, PageTable data) {
        List<PageTableString> strings = data.getStrings();
        int columns = data.getColumns() + 1;
        List<PageTableLegend> legend = data.getLegend();

        // First, collapse 'strings' into a header and content row
        if (strings.size() % columns != 0) {
            throw new IllegalArgumentException(
                String.format("Data should divide columns, got %d len and %d columns", 
                strings.size(), columns));
        }

        int rows = strings.size() / columns;

        if (rows <= 1) {
            throw new IllegalArgumentException(
                String.format("Should have > 1 rows, got %d", rows));
        }

        List<PageTableString> headers = strings.subList(0, columns);
        List<List<PageTableString>> body = new java.util.ArrayList<>();
        for (int i = 1; i < rows; i++) {
            body.add(strings.subList(i * columns, (i + 1) * columns));
        }

        // Title + text
        context.formatTitle(buffer, data.getTitle(), null);
        context.formatText(buffer, data.getText(), null);

        if (legend != null && !legend.isEmpty()) {
            buffer.add("<div class=\"row\"><div class=\"col-md-9\">");
        }

        // Build the HTML table
        buffer.add("<figure class=\"table-figure\"><table><thead><tr>");
        for (PageTableString header : headers) {
            buffer.add(getComponent(header, "th"));
        }
        buffer.add("</tr></thead><tbody>");
        for (List<PageTableString> row : body) {
            buffer.add("<tr>");
            for (PageTableString td : row) {
                buffer.add(getComponent(td, "td"));
            }
            buffer.add("</tr>");
        }
        buffer.add("</tbody></table></figure>");

        if (legend != null && !legend.isEmpty()) {
            buffer.add("</div><div class=\"col-md-3\"><h4>Legend</h4>");
            for (PageTableLegend entry : legend) {
                // These are just a color square followed by a name
                String color = entry.getColor().substring(2); // Remove the "2:" prefix
                String text = entry.getText();
                buffer.add(String.format(
                    """
                    <div class="item-header">
                        <span style="background-color:#%s"></span>
                        <p>%s</p>
                    </div>
                    """, color, text));
            }
            buffer.add("</div></div>");
        }
    }

    private static String getComponent(PageTableString th, String key) {
        if (th.getFill() != null) {
            // Solid fill
            String color = th.getFill().substring(2); // Remove the "2:" prefix
            return String.format("<%s style=\"background-color:#%s;\"></%s>", key, color, key);
        }

        String text = th.getText();
        if (text.isEmpty()) {
            return String.format("<%s></%s>", key, key);
        }

        if (th.isBold()) {
            return String.format("<%s><p style=\"font-weight: bold;\">%s</p></%s>", key, text, key);
        } else {
            return String.format("<%s><p>%s</p></%s>", key, text, key);
        }
    }
}