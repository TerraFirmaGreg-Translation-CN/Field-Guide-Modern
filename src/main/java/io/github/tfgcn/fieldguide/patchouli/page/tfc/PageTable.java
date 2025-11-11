package io.github.tfgcn.fieldguide.patchouli.page.tfc;

import com.google.gson.annotations.SerializedName;
import io.github.tfgcn.fieldguide.patchouli.page.IPageWithText;
import lombok.Data;

import java.util.List;

@Data
public class PageTable extends IPageWithText {
    private List<PageTableString> strings;
    private List<PageTableLegend> legend;
    private Integer columns;
    @SerializedName("first_column_width")
    private Integer firstColumnWidth;
    @SerializedName("column_width")
    private Integer columnWidth;
    @SerializedName("row_height")
    private Integer rowHeight;
    @SerializedName("left_buffer")
    private Integer leftBuffer;
    @SerializedName("top_buffer")
    private Integer topBuffer;

    private String title;

    @SerializedName("draw_background")
    private boolean drawBackground;
}
