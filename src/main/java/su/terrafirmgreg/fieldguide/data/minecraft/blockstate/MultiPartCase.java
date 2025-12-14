package su.terrafirmgreg.fieldguide.data.minecraft.blockstate;

import com.google.gson.annotations.JsonAdapter;
import su.terrafirmgreg.fieldguide.gson.BlockStateVariantListAdapter;
import su.terrafirmgreg.fieldguide.gson.BlockStateConditionAdapter;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class MultiPartCase {
    @JsonAdapter(BlockStateConditionAdapter.class)
    private Condition when;
    @JsonAdapter(BlockStateVariantListAdapter.class)
    private List<Variant> apply;

    public boolean check(Map<String, String> properties) {
        // 如果没有 when 条件，表示在所有方块状态中都适用
        if (when == null) {
            return true;
        }
        return when.check(properties);
    }
}