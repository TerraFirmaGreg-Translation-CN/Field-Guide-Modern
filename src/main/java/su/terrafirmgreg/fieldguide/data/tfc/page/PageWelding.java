package su.terrafirmgreg.fieldguide.data.tfc.page;

import su.terrafirmgreg.fieldguide.data.patchouli.page.IPageDoubleRecipe;
import lombok.Data;

@Data
public class PageWelding extends IPageDoubleRecipe {

    public PageWelding() {
        super("tfc:welding");
    }
}
