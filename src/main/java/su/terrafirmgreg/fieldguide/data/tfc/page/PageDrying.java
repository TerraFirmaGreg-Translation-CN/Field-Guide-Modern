package su.terrafirmgreg.fieldguide.data.tfc.page;

import su.terrafirmgreg.fieldguide.data.patchouli.page.IPageDoubleRecipe;
import lombok.Data;

@Data
public class PageDrying extends IPageDoubleRecipe {

    public PageDrying() {
        super("tfc:drying");
    }
}
