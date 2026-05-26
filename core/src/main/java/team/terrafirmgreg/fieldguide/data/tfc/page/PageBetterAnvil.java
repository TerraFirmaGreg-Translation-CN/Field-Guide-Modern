package team.terrafirmgreg.fieldguide.data.tfc.page;

import team.terrafirmgreg.fieldguide.data.patchouli.page.IPageWithText;
import lombok.Data;

/**
 * SNS modpack page: four anvil recipes in a 2×2 grid ({@code recipe} … {@code recipe4}).
 */
@Data
public class PageBetterAnvil extends IPageWithText {

    private String recipe;
    private String recipe2;
    private String recipe3;
    private String recipe4;
    private String text4;
}
