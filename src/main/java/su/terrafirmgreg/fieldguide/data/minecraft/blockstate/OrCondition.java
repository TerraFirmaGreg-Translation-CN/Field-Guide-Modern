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
public class OrCondition implements Condition {
    @SerializedName("OR")
    private List<Condition> or = new ArrayList<>();

    @Override
    public boolean check(Map<String, String> properties) {
        if (or == null || or.isEmpty()) {
            return false;
        }

        for (Condition subCondition : or) {
            if (subCondition.check(properties)) {
                return true;
            }
        }
        return false;
    }
}
