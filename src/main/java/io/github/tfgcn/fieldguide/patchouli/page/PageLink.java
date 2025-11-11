package io.github.tfgcn.fieldguide.patchouli.page;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class PageLink extends PageText {

    private String url;

    @SerializedName("link_text")
    private String linkText;
}
