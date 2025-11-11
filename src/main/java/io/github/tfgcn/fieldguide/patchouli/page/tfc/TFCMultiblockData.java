package io.github.tfgcn.fieldguide.patchouli.page.tfc;

import com.google.gson.annotations.JsonAdapter;
import io.github.tfgcn.fieldguide.gsonadapter.TFCMultiblockDataAdapter;
import io.github.tfgcn.fieldguide.patchouli.page.PageMultiblockData;
import lombok.Data;

import java.util.Map;

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