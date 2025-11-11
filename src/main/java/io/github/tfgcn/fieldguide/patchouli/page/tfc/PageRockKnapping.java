package io.github.tfgcn.fieldguide.patchouli.page.tfc;

import io.github.tfgcn.fieldguide.patchouli.page.IPageDoubleRecipe;
import lombok.Data;

import java.util.List;

@Data
public class PageRockKnapping extends IPageDoubleRecipe {

    private List<String> recipes;

    public PageRockKnapping() {
        super("tfc:rock_knapping");
    }
}
