package io.github.tfgcn.fieldguide.minecraft;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Condition {
    @SerializedName("OR")
    public List<Condition> or;
    private Map<String, String> otherConditions;
}