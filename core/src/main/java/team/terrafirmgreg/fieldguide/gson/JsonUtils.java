package team.terrafirmgreg.fieldguide.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import team.terrafirmgreg.fieldguide.data.recipe.Recipe;
import team.terrafirmgreg.fieldguide.data.patchouli.BookPage;
import team.terrafirmgreg.fieldguide.data.recipe.RecipeResult;
import team.terrafirmgreg.fieldguide.data.recipe.adapter.IngredientDeserializer;
import team.terrafirmgreg.fieldguide.data.recipe.adapter.RecipeDeserializer;
import team.terrafirmgreg.fieldguide.data.recipe.adapter.RecipeResultDeserializer;
import team.terrafirmgreg.fieldguide.data.recipe.Ingredient;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * desc:
 *
 * @author yanmaoyuan
 */
public final class JsonUtils {

    public static final Gson GSON;

    static {
        GSON = new GsonBuilder()
                .setObjectToNumberStrategy(ToNumberPolicy.LAZILY_PARSED_NUMBER)
                .registerTypeHierarchyAdapter(BookPage.class, new LexiconPageAdapter())
                .registerTypeAdapter(Ingredient.class, new IngredientDeserializer())
                .registerTypeAdapter(RecipeResult.class, new RecipeResultDeserializer())
                .registerTypeAdapter(Recipe.class, new RecipeDeserializer())
                .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
                .disableHtmlEscaping()
                .create();
    }

    private JsonUtils() {}

    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }
    public static <T> T fromJson(String json, Type clazz) {
        return GSON.fromJson(json, clazz);
    }

    public static <T> void writeFile(File file, T obj) throws IOException {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(obj, writer);
        }
    }

    public static <T> T readFile(File file, Type type) throws IOException {
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        }
    }

    public static <T> T readFile(InputStream inputStream, Type type) throws IOException {
        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        }
    }
}
