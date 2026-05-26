package team.terrafirmgreg.fieldguide.export;

import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import team.terrafirmgreg.fieldguide.generated.RecipeImagePaths;
import team.terrafirmgreg.fieldguide.gson.JsonUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Slf4j
public class RecipeImageCatalog {

    private static final TypeToken<Map<String, Object>> MAP_TYPE = new TypeToken<>() {};

    private final Map<String, String> recipeToFile;

    private RecipeImageCatalog(Map<String, String> recipeToFile) {
        this.recipeToFile = recipeToFile;
    }

    public static RecipeImageCatalog load(Path exportRoot) {
        Path indexFile = exportRoot.resolve(RecipeImagePaths.GENERATED_DIR).resolve(RecipeImagePaths.INDEX_FILE);
        if (!Files.isRegularFile(indexFile)) {
            log.warn("No recipe image index at {}", indexFile);
            return new RecipeImageCatalog(Map.of());
        }
        try {
            String json = Files.readString(indexFile);
            Map<String, Object> root = JsonUtils.GSON.fromJson(json, MAP_TYPE.getType());
            if (root == null) {
                return new RecipeImageCatalog(Map.of());
            }
            @SuppressWarnings("unchecked")
            Map<String, String> recipes = (Map<String, String>) root.get("recipes");
            if (recipes == null) {
                return new RecipeImageCatalog(Map.of());
            }
            Map<String, String> verified = new TreeMap<>();
            for (Map.Entry<String, String> e : recipes.entrySet()) {
                Path png = exportRoot.resolve(RecipeImagePaths.GENERATED_DIR).resolve(e.getValue());
                if (Files.isRegularFile(png)) {
                    verified.put(e.getKey(), e.getValue());
                }
            }
            log.info("Loaded {} pre-rendered recipe images", verified.size());
            return new RecipeImageCatalog(Collections.unmodifiableMap(verified));
        } catch (IOException e) {
            log.warn("Failed to read recipe image index {}", indexFile, e);
            return new RecipeImageCatalog(Map.of());
        }
    }

    public boolean hasImage(String recipeId) {
        return recipeToFile.containsKey(recipeId);
    }

    public Optional<String> relativeImagePath(String recipeId) {
        String file = recipeToFile.get(recipeId);
        if (file == null) {
            return Optional.empty();
        }
        return Optional.of(RecipeImagePaths.GENERATED_DIR + "/" + file);
    }
}
