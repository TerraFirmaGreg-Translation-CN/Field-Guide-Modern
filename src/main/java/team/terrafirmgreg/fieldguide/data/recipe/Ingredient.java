package team.terrafirmgreg.fieldguide.data.recipe;

import team.terrafirmgreg.fieldguide.asset.ItemImageResult;
import team.terrafirmgreg.fieldguide.render.TextureRenderer;

public interface Ingredient {
    ItemImageResult toItemImage(TextureRenderer renderer);
}