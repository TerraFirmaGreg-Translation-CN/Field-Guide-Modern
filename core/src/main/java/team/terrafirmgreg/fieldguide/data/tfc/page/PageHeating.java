package team.terrafirmgreg.fieldguide.data.tfc.page;

import team.terrafirmgreg.fieldguide.data.patchouli.page.IPageDoubleRecipe;
import lombok.Data;

@Data
public class PageHeating extends IPageDoubleRecipe {

    public PageHeating() {
        super("tfc:heating");
    }
}
