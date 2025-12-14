package su.terrafirmgreg.fieldguide.data.patchouli.page;

import lombok.Data;

import java.util.Map;

@Data
public class PageMultiblockData {
    private Map<String, String> mapping;
    private String[][] pattern;
    private Boolean symmetrical;
    private int[] offset;
}
