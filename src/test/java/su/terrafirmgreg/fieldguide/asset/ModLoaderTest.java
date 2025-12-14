package su.terrafirmgreg.fieldguide.asset;

import su.terrafirmgreg.fieldguide.data.fml.ModLoader;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * desc:
 *
 * @author yanmaoyuan
 */
@Slf4j
public class ModLoaderTest {

    @Test
    void testLoader() throws IOException {
        Path path = Paths.get("Modpack-Modern", "mods");
        ModLoader loader = new ModLoader(path);
        loader.getLoadedMods();
    }
}
