package team.terrafirmgreg.fieldguide.data.minecraft.tag;

import lombok.Data;

import java.util.List;

@Data
public class Tags {
    private Boolean replace = false;
    private List<TagElement> values;
}