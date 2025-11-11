package io.github.tfgcn.fieldguide.patchouli.page.tfc;

import io.github.tfgcn.fieldguide.patchouli.page.IPageDoubleRecipe;
import lombok.Data;

@Data
public class PageHeating extends IPageDoubleRecipe {

    public PageHeating() {
        super("tfc:heating");
    }
}
