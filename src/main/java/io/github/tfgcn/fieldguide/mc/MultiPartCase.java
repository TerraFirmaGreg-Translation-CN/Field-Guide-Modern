package io.github.tfgcn.fieldguide.mc;

import lombok.Data;

@Data
public class MultiPartCase {
    public Condition when;
    public ModelApply apply;
}