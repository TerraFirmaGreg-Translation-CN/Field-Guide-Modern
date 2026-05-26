package team.terrafirmgreg.fieldguide.export.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import team.terrafirmgreg.fieldguide.generated.RecipeLayoutPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Copies GUI textures referenced by EMI layout export into {@link RecipeLayoutPaths#TEXTURES_DIR}.
 */
public final class RecipeTextureExporter {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RecipeTextureExporter() {}

    public record Result(int requested, int written, int missing, long pngBytes) {}

    public static Result export(Path outputDir, Minecraft client, Set<String> textureIds) throws IOException {
        Path texRoot = outputDir.resolve(RecipeLayoutPaths.TEXTURES_DIR);
        if (Files.exists(texRoot)) {
            Files.walk(texRoot)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        }
        Files.createDirectories(texRoot);

        // EMI recipe chrome
        Set<String> all = new TreeSet<>(textureIds);
        all.add("emi:textures/gui/widgets.png");
        all.add("emi:textures/gui/background.png");

        Map<String, String> manifest = new TreeMap<>();
        int written = 0;
        int missing = 0;
        long bytes = 0;

        var rm = client.getResourceManager();
        for (String idStr : all) {
            ResourceLocation id = ResourceLocation.parse(idStr);
            String rel = textureRelativePath(id);
            Path out = texRoot.resolve(rel);
            Files.createDirectories(out.getParent());

            var opt = rm.getResource(id);
            if (opt.isEmpty()) {
                missing++;
                LOGGER.warn("[recipe-textures] missing {}", idStr);
                continue;
            }
            try {
                Resource resource = opt.get();
                try (InputStream in = resource.open()) {
                    NativeImage image = NativeImage.read(in);
                    try {
                        image.writeToFile(out);
                        bytes += Files.size(out);
                        manifest.put(idStr, rel.replace('\\', '/'));
                        written++;
                    } finally {
                        image.close();
                    }
                }
            } catch (Exception e) {
                missing++;
                LOGGER.warn("[recipe-textures] failed {}: {}", idStr, e.toString());
            }
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema", 1);
        root.put("textures", manifest);
        Files.writeString(texRoot.resolve(RecipeLayoutPaths.TEXTURE_MANIFEST_FILE), GSON.toJson(root));

        LOGGER.info("[recipe-textures] {}/{} written ({} bytes), {} missing",
                written, all.size(), bytes, missing);
        return new Result(all.size(), written, missing, bytes);
    }

    /** {@code emi:textures/gui/widgets.png} → {@code emi/textures/gui/widgets.png} */
    static String textureRelativePath(ResourceLocation id) {
        return id.getNamespace() + "/" + id.getPath();
    }
}
