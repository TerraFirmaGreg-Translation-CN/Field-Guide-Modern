package su.terrafirmgreg.fieldguide.data.recipe;

import su.terrafirmgreg.fieldguide.asset.ItemImageResult;
import su.terrafirmgreg.fieldguide.render.TextureRenderer;

public interface Ingredient {
    ItemImageResult toItemImage(TextureRenderer renderer);
}