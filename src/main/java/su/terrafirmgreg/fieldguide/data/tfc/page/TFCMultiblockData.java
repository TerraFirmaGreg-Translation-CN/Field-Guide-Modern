package su.terrafirmgreg.fieldguide.data.tfc.page;

import com.google.gson.annotations.JsonAdapter;
import su.terrafirmgreg.fieldguide.gson.TFCMultiblockDataAdapter;
import su.terrafirmgreg.fieldguide.data.patchouli.page.PageMultiblockData;
import lombok.Data;

@Data
@JsonAdapter(TFCMultiblockDataAdapter.class)
public class TFCMultiblockData extends PageMultiblockData {
    private String multiblockId;

    public TFCMultiblockData() {
    }

    public TFCMultiblockData(String multiblockId) {
        this.multiblockId = multiblockId;
    }
}