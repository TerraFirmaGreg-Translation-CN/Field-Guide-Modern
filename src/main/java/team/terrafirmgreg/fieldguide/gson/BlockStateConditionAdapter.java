package team.terrafirmgreg.fieldguide.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import team.terrafirmgreg.fieldguide.data.minecraft.blockstate.AndCondition;
import team.terrafirmgreg.fieldguide.data.minecraft.blockstate.Condition;
import team.terrafirmgreg.fieldguide.data.minecraft.blockstate.OrCondition;
import team.terrafirmgreg.fieldguide.data.minecraft.blockstate.PropertyCondition;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
public class BlockStateConditionAdapter extends TypeAdapter<Condition> {

    @Override
    public void write(JsonWriter out, Condition value) throws IOException {
        // 序列化，根据不同的子类来写
        switch (value) {
            case AndCondition and -> {
                out.beginObject();
                out.name("AND");
                out.beginArray();
                for (Condition condition : and.getAdd()) {
                    this.write(out, condition);
                }
                out.endArray();
                out.endObject();
            }
            case OrCondition or -> {
                out.beginObject();
                out.name("OR");
                out.beginArray();
                for (Condition condition : or.getOr()) {
                    this.write(out, condition);
                }
                out.endArray();
                out.endObject();
            }
            case PropertyCondition property -> {
                out.beginObject();
                if (property.getConditions() != null) {
                    for (Map.Entry<String, String> entry : property.getConditions().entrySet()) {
                        out.name(entry.getKey());
                        out.value(entry.getValue());
                    }
                }
                out.endObject();
            }
            default -> throw new IllegalArgumentException("Unknown condition type: " + value.getClass());
        }
    }

    @Override
    public Condition read(JsonReader in) throws IOException {
        // 读取一个JSON对象
        in.beginObject();
        String firstKey = in.nextName();
        JsonToken token = in.peek();

        if ("AND".equals(firstKey)) {
            AndCondition andCondition = new AndCondition();
            in.beginArray();
            while (in.hasNext()) {
                Condition condition = this.read(in);
                andCondition.getAdd().add(condition);
            }
            in.endArray();
            in.endObject();
            return andCondition;
        } else if ("OR".equals(firstKey)) {
            OrCondition orCondition = new OrCondition();
            in.beginArray();
            while (in.hasNext()) {
                Condition condition = this.read(in);
                orCondition.getOr().add(condition);
            }
            in.endArray();
            in.endObject();
            return orCondition;
        } else {
            return readPropertyCondition(firstKey, in);
        }
    }

    private PropertyCondition readPropertyCondition(String firstKey, JsonReader in) throws IOException {
        Map<String, String> properties = new TreeMap<>();

        put(properties, firstKey, in);
        while (in.hasNext()) {
            String name = in.nextName();
            put(properties, name, in);
        }
        in.endObject();

        PropertyCondition propertyCondition = new PropertyCondition();
        propertyCondition.setConditions(properties);
        return propertyCondition;
    }

    private void put(Map<String, String> map, String key, JsonReader in) throws IOException {
        JsonToken token = in.peek();
        switch(in.peek()) {
            case STRING -> map.put(key, in.nextString());
            case BOOLEAN -> map.put(key, String.valueOf(in.nextBoolean()));
            case NUMBER -> map.put(key, String.valueOf(in.nextInt()));// FIXME 如何判断是小数点还是整数？
            default -> log.info("Unknown token type: {}, {}", key, token);
        }
    }
}