package io.github.tfgcn.fieldguide.minecraft;

import lombok.Data;

import java.util.List;

@Data
public class Tags {
    private Boolean replace = false;
    private List<TagElement> values;
}