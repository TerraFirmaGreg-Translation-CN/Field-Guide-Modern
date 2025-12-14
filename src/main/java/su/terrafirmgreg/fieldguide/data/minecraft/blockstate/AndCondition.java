package su.terrafirmgreg.fieldguide.data.minecraft.blockstate;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * desc:
 *
 * @author yanmaoyuan
 */
@Data
public class AndCondition implements Condition {
    @SerializedName("AND")
    private List<Condition> add = new ArrayList<>();

    @Override
    public boolean check(Map<String, String> properties) {
        if (add == null || add.isEmpty()) {
            return false;
        }

        for (Condition subCondition : add) {
            if (!subCondition.check(properties)) {
                return false;
            }
        }
        return true;
    }
}
