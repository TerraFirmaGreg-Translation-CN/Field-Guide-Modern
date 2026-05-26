package team.terrafirmgreg.fieldguide.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KnappingHtmlRendererTest {

    @Test
    void rendersFiveByFiveGrid() {
        List<String> pattern = List.of("XXXXX", "XX   ", "     ", "XXXXX", "XX   ");
        String html = KnappingHtmlRenderer.renderOverlay(pattern, false, "rock");
        assertTrue(html.contains("knapping-grid"));
        assertTrue(html.split("knapping-cell").length > 25);
    }
}
