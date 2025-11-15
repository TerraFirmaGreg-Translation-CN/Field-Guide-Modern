package io.github.tfgcn.fieldguide.minecraft;

import com.google.gson.annotations.JsonAdapter;
import io.github.tfgcn.fieldguide.gsonadapter.TagElementAdapter;
import lombok.Data;

@JsonAdapter(TagElementAdapter.class)
@Data
public class TagElement {
    // id or tag
    // if it's a tag: #<namespace>:<name>
    // if it's an id: <namespace>:<name>
    private String id;
    private Boolean required = true;

    public boolean isTagReference() {
        return id != null && id.startsWith("#");
    }

    public String getName() {
        if (isTagReference()) {
            return id.substring(1);
        }
        return id;
    }

    public String getNamespace() {
        if (id == null) return null;
        String actualId = isTagReference() ? id.substring(1) : id;
        int colonIndex = actualId.indexOf(':');
        return colonIndex >= 0 ? actualId.substring(0, colonIndex) : "minecraft";
    }

    public String getPath() {
        if (id == null) return null;
        String actualId = isTagReference() ? id.substring(1) : id;
        int colonIndex = actualId.indexOf(':');
        return colonIndex >= 0 ? actualId.substring(colonIndex + 1) : actualId;
    }

    public boolean isRequired() {
        return required != null ? required : true;
    }
}
