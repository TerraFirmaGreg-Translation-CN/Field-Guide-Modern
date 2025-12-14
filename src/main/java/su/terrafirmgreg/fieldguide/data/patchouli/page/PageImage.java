package su.terrafirmgreg.fieldguide.data.patchouli.page;

import su.terrafirmgreg.fieldguide.data.patchouli.BookPage;
import lombok.Data;

import java.util.List;

@Data
public class PageImage extends BookPage {

    /**
     * An array with images to display. Images should be in resource location format.
     * For example, the value `botania:textures/gui/entries/banners.png` will point to
     * `/assets/botania/textures/gui/entries/banners.png` in the resource pack.
     *
     * For modpack creators, this means that any images you want to use must be loaded
     * with an external resource pack (or a mod such as Open Loader).
     *
     * For best results, make your image file 256 by 256, but only place content in the
     * upper left 200 by 200 area. This area is then rendered at a 0.5x scale compared
     * to the rest of the book in pixel size.
     *
     * If there's more than one image in this array, arrow buttons are shown like in
     * the picture, allowing the viewer to switch between images.
     */
    private List<String> images;

    /**
     * The title of the page, shown above the image.
     */
    private String title;

    /**
     * Defaults to false. Set to true if you want the image to be bordered, like in
     * the picture. It's suggested that border is set to true for images that use
     * the entire canvas, whereas images that don't touch the corners shouldn't have it.
     */
    private Boolean border = false;

    /**
     * The text to display on this page, under the image. This text can be formatted.
     */
    private String text;
}
