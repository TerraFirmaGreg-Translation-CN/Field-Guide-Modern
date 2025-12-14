package su.terrafirmgreg.fieldguide.data.patchouli.page;

import lombok.Data;

@Data
public class IPageDoubleRecipe extends IPageWithText {
    private String recipeType;

    private String recipe;
    private String recipe2;
    private String title;

    IPageDoubleRecipe() {}
    protected IPageDoubleRecipe(String recipeType) {
        this.recipeType = recipeType;
    }
}