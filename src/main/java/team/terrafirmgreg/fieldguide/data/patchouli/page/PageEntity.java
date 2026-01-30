package team.terrafirmgreg.fieldguide.data.patchouli.page;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class PageEntity extends IPageWithText {

    @SerializedName("entity")
    private String entityId;

    private float scale = 1F;

    private float extraOffset = 0F;

    private String name;

    @SerializedName("default_rotation")
    private float defaultRotation = -45F;
}
