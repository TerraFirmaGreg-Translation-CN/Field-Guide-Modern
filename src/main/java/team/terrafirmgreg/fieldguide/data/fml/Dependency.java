package team.terrafirmgreg.fieldguide.data.fml;

import lombok.Data;

/**
 * desc:
 *
 * @author yanmaoyuan
 */
@Data
public class Dependency {
    private final String modId;
    private final boolean mandatory;
    private final DependencyOrdering ordering;
}
