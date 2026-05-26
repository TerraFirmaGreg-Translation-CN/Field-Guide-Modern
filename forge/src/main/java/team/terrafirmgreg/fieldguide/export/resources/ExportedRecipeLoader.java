package team.terrafirmgreg.fieldguide.export.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads per-namespace {@code data/<ns>/recipes.json} files from {@link RecipeBundleExporter}. */
public final class ExportedRecipeLoader {

    private ExportedRecipeLoader() {}

    public static List<JsonElement> loadAll(Path outputDir) throws IOException {
        Path dataRoot = outputDir.resolve("data");
        List<JsonElement> recipes = new ArrayList<>();
        if (!Files.isDirectory(dataRoot)) {
            return recipes;
        }
        try (var walk = Files.walk(dataRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals("recipes.json"))
                    .forEach(p -> {
                        try (Reader reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                            JsonObject bundle = JsonParser.parseReader(reader).getAsJsonObject();
                            for (var entry : bundle.entrySet()) {
                                recipes.add(entry.getValue());
                            }
                        } catch (IOException e) {
                            // skip broken file
                        }
                    });
        }
        return recipes;
    }
}
