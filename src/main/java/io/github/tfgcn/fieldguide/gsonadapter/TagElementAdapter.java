package io.github.tfgcn.fieldguide.gsonadapter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.github.tfgcn.fieldguide.minecraft.TagElement;

import java.io.IOException;

public class TagElementAdapter extends TypeAdapter<TagElement> {

    @Override
    public void write(JsonWriter out, TagElement value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        if (value.getRequired() == null) {
            out.value(value.getId());
        } else {
            out.beginObject();
            out.name("id").value(value.getId());
            out.name("required").value(value.getRequired());
            out.endObject();
        }
    }

    @Override
    public TagElement read(JsonReader in) throws IOException {
        TagElement tagElement = new TagElement();

        switch (in.peek()) {
            case STRING:
                // "namespace:name" or "#namespace:name"
                tagElement.setId(in.nextString());
                break;
            case BEGIN_OBJECT:
                // {"id": "...", "required": true/false}
                in.beginObject();
                while (in.hasNext()) {
                    String name = in.nextName();
                    switch (name) {
                        case "id":
                            tagElement.setId(in.nextString());
                            break;
                        case "required":
                            tagElement.setRequired(in.nextBoolean());
                            break;
                        default:
                            in.skipValue();
                            break;
                    }
                }
                in.endObject();
                break;
            default:
                throw new IOException("Invalid tag element: expected string or object, got " + in.peek());
        }

        return tagElement;
    }
}
