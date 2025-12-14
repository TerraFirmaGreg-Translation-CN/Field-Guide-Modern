package su.terrafirmgreg.fieldguide.data.patchouli;

import java.util.ArrayList;
import java.util.List;

/**
 * desc:
 *
 * @author yanmaoyuan
 */
public class ItemStackParser {

    public static String[] splitStacksFromSerializedIngredient(String ingredientSerialized) {
        final List<String> result = new ArrayList<>();

        int lastIndex = 0;
        int braces = 0;
        Character insideString = null;
        for (int i = 0; i < ingredientSerialized.length(); i++) {
            switch (ingredientSerialized.charAt(i)) {
                case '{':
                    if (insideString == null) {
                        braces++;
                    }
                    break;
                case '}':
                    if (insideString == null) {
                        braces--;
                    }
                    break;
                case '\'':
                    insideString = insideString == null ? '\'' : null;
                    break;
                case '"':
                    insideString = insideString == null ? '"' : null;
                    break;
                case ',':
                    if (braces <= 0) {
                        result.add(ingredientSerialized.substring(lastIndex, i));
                        lastIndex = i + 1;
                        break;
                    }
            }
        }

        result.add(ingredientSerialized.substring(lastIndex));

        return result.toArray(new String[0]);
    }
}
