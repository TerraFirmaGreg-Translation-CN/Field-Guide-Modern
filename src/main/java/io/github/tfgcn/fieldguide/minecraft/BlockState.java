package io.github.tfgcn.fieldguide.minecraft;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class BlockState {
    public Map<String, List<Variant>> variants;
    public List<MultiPartCase> multipart;
}