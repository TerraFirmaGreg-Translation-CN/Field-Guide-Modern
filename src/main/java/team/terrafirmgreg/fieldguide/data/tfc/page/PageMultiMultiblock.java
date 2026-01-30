package team.terrafirmgreg.fieldguide.data.tfc.page;

import team.terrafirmgreg.fieldguide.data.patchouli.page.IPageWithText;
import lombok.Data;

import java.util.List;

@Data
public class PageMultiMultiblock extends IPageWithText {

    /**
     * @see <a href="https://vazkiimods.github.io/Patchouli/docs/patchouli-basics/multiblocks">Defining Multiblocks</a>
     */
    private List<TFCMultiblockData> multiblocks;
}
