package su.terrafirmgreg.fieldguide.data.tfc.page;

import su.terrafirmgreg.fieldguide.data.patchouli.page.IPageDoubleRecipe;
import lombok.Data;

@Data
public class PageLoom extends IPageDoubleRecipe {

    public PageLoom() {
        super("tfc:loom");
    }
}
