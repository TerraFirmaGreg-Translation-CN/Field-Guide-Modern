package su.terrafirmgreg.fieldguide.data.patchouli.page;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageSpotlightItem {
    private String type;// tag or item
    private String text;
}
