package team.terrafirmgreg.fieldguide.export.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import team.terrafirmgreg.fieldguide.generated.EmiBundlePaths;
import team.terrafirmgreg.fieldguide.generated.RecipeLayoutPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes {@link EmiBundlePaths#BUNDLE_FILE} (languages, layout schema, scale). */
public final class EmiBundleManifestWriter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private EmiBundleManifestWriter() {}

    public static void write(
            Path outputDir,
            List<String> languages,
            int layoutScale,
            int recipeCount) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema", 1);
        root.put("layoutSchema", RecipeLayoutPaths.SCHEMA_VERSION);
        root.put("scale", layoutScale);
        root.put("defaultLanguage", EmiBundlePaths.DEFAULT_LANGUAGE);
        root.put("languages", languages);
        root.put("recipeCount", recipeCount);

        Path out = EmiBundlePaths.resolve(outputDir, EmiBundlePaths.BUNDLE_FILE);
        Files.createDirectories(out.getParent());
        Files.writeString(out, GSON.toJson(root));
    }
}
