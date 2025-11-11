package io.github.tfgcn.fieldguide.patchouli.page;

import lombok.Data;

@Data
public class PageText extends IPageWithText {

    /**
     * An optional title to display at the top of the page. If you set this,
     * the rest of the text will be shifted down a bit.
     * You can't use "title" in the first page of an entry.
     */
    private String title;
}
