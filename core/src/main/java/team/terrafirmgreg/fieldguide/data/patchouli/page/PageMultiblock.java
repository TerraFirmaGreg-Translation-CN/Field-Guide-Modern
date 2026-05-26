package team.terrafirmgreg.fieldguide.data.patchouli.page;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class PageMultiblock extends IPageWithText {

    private String name;

    @SerializedName("multiblock_id")
    private String multiblockId;

    /**
     * @see <a href="https://vazkiimods.github.io/Patchouli/docs/patchouli-basics/multiblocks">Defining Multiblocks</a>
     */
    private PageMultiblockData multiblock;

    @SerializedName("enable_visualize")
    private boolean enableVisualize;
}
