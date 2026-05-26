package team.terrafirmgreg.fieldguide.data.tfc.page;

import team.terrafirmgreg.fieldguide.data.patchouli.page.IPageDoubleRecipe;
import lombok.Data;

@Data
public class PageWelding extends IPageDoubleRecipe {

    public PageWelding() {
        super("tfc:welding");
    }
}
