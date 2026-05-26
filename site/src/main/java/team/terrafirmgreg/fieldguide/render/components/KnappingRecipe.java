package team.terrafirmgreg.fieldguide.render.components;

public record KnappingRecipe(String recipeId, String image, String overlayHtml) {

    public KnappingRecipe(String recipeId, String image) {
        this(recipeId, image, null);
    }

    public boolean usesHtmlOverlay() {
        return overlayHtml != null && !overlayHtml.isEmpty();
    }
}