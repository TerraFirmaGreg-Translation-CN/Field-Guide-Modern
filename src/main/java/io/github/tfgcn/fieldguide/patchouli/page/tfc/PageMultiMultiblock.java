package io.github.tfgcn.fieldguide.patchouli.page.tfc;

import io.github.tfgcn.fieldguide.patchouli.page.IPageWithText;
import lombok.Data;

import java.util.List;

@Data
public class PageMultiMultiblock extends IPageWithText {

    /**
     * @see <a href="https://vazkiimods.github.io/Patchouli/docs/patchouli-basics/multiblocks">Defining Multiblocks</a>
     */
    private List<TFCMultiblockData> multiblocks;
}
