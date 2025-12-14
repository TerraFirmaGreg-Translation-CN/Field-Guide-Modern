package su.terrafirmgreg.fieldguide.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import su.terrafirmgreg.fieldguide.data.minecraft.blockstate.Variant;

import java.io.IOException;
import java.util.*;

public class BlockStateVariantMapAdapter extends TypeAdapter<Map<String, List<Variant>>> {

    @Override
    public void write(JsonWriter out, Map<String, List<Variant>> value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        out.beginObject();
        for (Map.Entry<String, List<Variant>> entry : value.entrySet()) {
            out.name(entry.getKey());
            List<Variant> variants = entry.getValue();
            if (variants.size() == 1) {
                writeVariant(out, variants.getFirst());
            } else {
                out.beginArray();
                for (Variant variant : variants) {
                    writeVariant(out, variant);
                }
                out.endArray();
            }
        }
        out.endObject();
    }

    @Override
    public Map<String, List<Variant>> read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        Map<String, List<Variant>> result = new LinkedHashMap<>();

        in.beginObject();
        while (in.hasNext()) {
            String key = in.nextName();
            List<Variant> variants = readVariantList(in);
            result.put(key, variants);
        }
        in.endObject();

        return result;
    }

    private List<Variant> readVariantList(JsonReader in) throws IOException {
        List<Variant> variants = new ArrayList<>();

        JsonToken token = in.peek();
        if (token == JsonToken.BEGIN_OBJECT) {
            Variant variant = readVariant(in);
            variants.add(variant);
        } else if (token == JsonToken.BEGIN_ARRAY) {
            in.beginArray();
            while (in.hasNext()) {
                Variant variant = readVariant(in);
                variants.add(variant);
            }
            in.endArray();
        } else {
            throw new IllegalStateException("Expected object or array, was: " + token);
        }

        return variants;
    }

    private Variant readVariant(JsonReader in) throws IOException {
        Variant variant = new Variant();

        in.beginObject();
        while (in.hasNext()) {
            String fieldName = in.nextName();
            switch (fieldName) {
                case "model":
                    variant.setModel(in.nextString());
                    break;
                case "x":
                    variant.setX(in.nextInt());
                    break;
                case "y":
                    variant.setY(in.nextInt());
                    break;
                case "z":
                    // 处理新增的 z 轴旋转
                    variant.setZ(in.nextInt());
                    break;
                case "uvlock":
                    variant.setUvlock(in.nextBoolean());
                    break;
                case "weight":
                    variant.setWeight(in.nextInt());
                    break;
                default:
                    in.skipValue(); // 忽略未知字段
                    break;
            }
        }
        in.endObject();

        return variant;
    }

    private void writeVariant(JsonWriter out, Variant variant) throws IOException {
        out.beginObject();
        out.name("model").value(variant.getModel());

        if (variant.getX() != 0) {
            out.name("x").value(variant.getX());
        }
        if (variant.getY() != 0) {
            out.name("y").value(variant.getY());
        }
        if (variant.getZ() != 0) {
            out.name("z").value(variant.getZ());
        }
        if (variant.getUvlock() != null) {
            out.name("uvlock").value(variant.getUvlock());
        }
        if (variant.getWeight() != 1) {
            out.name("weight").value(variant.getWeight());
        }

        out.endObject();
    }
}