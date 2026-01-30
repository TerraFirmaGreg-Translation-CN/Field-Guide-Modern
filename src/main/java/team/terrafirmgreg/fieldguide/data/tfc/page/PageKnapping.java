package team.terrafirmgreg.fieldguide.data.tfc.page;

import team.terrafirmgreg.fieldguide.data.patchouli.page.IPageDoubleRecipe;
import lombok.Data;

@Data
public class PageKnapping extends IPageDoubleRecipe {

    public PageKnapping() {
        super("tfc:knapping");
    }
}
