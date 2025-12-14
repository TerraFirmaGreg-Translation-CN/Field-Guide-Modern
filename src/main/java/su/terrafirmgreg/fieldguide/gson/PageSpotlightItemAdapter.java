package su.terrafirmgreg.fieldguide.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import su.terrafirmgreg.fieldguide.data.patchouli.ItemStackParser;
import su.terrafirmgreg.fieldguide.data.patchouli.page.PageSpotlightItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * // vanilla format, single item
 * {
 *   "type": "patchouli:spotlight",
 *   "item": {
 *     "item": "minecraft:diamond_sword"
 *   }
 * }
 *
 * // vanilla format, tag
 * {
 *   "type": "patchouli:spotlight",
 *   "item": {
 *     "tag": "minecraft:axes"
 *   }
 * }
 *
 * // patchouli string format, allowing interspersed items and tags
 * {
 *   "type": "patchouli:spotlight",
 *   "item": "minecraft:diamond_sword,tag:minecraft:axes,minecraft:diamond_shovel"
 * }
 *
 * @author yanmaoyuan
 */
public class PageSpotlightItemAdapter extends TypeAdapter<List<PageSpotlightItem>> {
    @Override
    public void write(JsonWriter out, List<PageSpotlightItem> value) {
        throw new UnsupportedOperationException("Serialization is not supported");
    }

    @Override
    public List<PageSpotlightItem> read(JsonReader in) throws IOException {
        List<PageSpotlightItem> list = new ArrayList<>();

        switch (in.peek()) {
            case BEGIN_OBJECT:
                in.beginObject();
                while (in.hasNext()) {
                    String name = in.nextName();
                    if ("item".equals(name)) {
                        String itemId = in.nextString();
                        list.add(new PageSpotlightItem("item", itemId));
                    } else if ("tag".equals(name)) {
                        String tagId = in.nextString();
                        list.add(new PageSpotlightItem("tag", tagId));
                    } else {
                        in.skipValue();
                    }
                }
                in.endObject();
                break;
            case STRING:
                String[] parts = ItemStackParser.splitStacksFromSerializedIngredient(in.nextString());
                for (String part : parts) {
                    part = part.trim();
                    if (part.startsWith("tag:")) {
                        list.add(new PageSpotlightItem("tag", part.substring(4)));
                    } else {
                        list.add(new PageSpotlightItem("item", part));
                    }
                }
                break;
            default:
                in.skipValue();
                break;
        }
        return list;
    }
}
