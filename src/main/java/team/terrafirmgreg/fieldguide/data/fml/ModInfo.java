package team.terrafirmgreg.fieldguide.data.fml;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModInfo {
    private String modId;
    private String name;
    private String version;
    private List<Dependency> dependencies;
    private Path jarPath;
    private int loadOrder;
}