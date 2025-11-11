package io.github.tfgcn.fieldguide.patchouli.page;

import io.github.tfgcn.fieldguide.patchouli.BookPage;
import lombok.Data;

@Data
public abstract class IPageWithText extends BookPage {

     /**
      * The text to display on this page.
      * This text can be <a href="https://vazkiimods.github.io/Patchouli/docs/patchouli-basics/text-formatting">formatted</a>.
      */
     protected String text;
}