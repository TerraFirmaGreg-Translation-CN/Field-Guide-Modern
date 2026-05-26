package team.terrafirmgreg.fieldguide.export.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Encodes live {@link net.minecraft.world.item.crafting.RecipeManager} entries into datapack-shaped JSON.
 */
final class RecipeJsonSerializer {

    private static final Logger LOGGER = LogManager.getLogger("fieldguide");

    private static final String GT_RECIPE_CLASS = "com.gregtechceu.gtceu.api.recipe.GTRecipe";
    private static final String GT_RECIPE_SERIALIZER_CLASS = "com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer";

    private RecipeJsonSerializer() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    static JsonObject toJson(ResourceLocation recipeId, Recipe<?> recipe) {
        RecipeSerializer serializer = recipe.getSerializer();
        ResourceLocation typeId = BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer);
        if (typeId == null) {
            throw new IllegalStateException("no serializer id for recipe " + recipeId);
        }

        JsonObject body = new JsonObject();
        if (!tryEncodeGtRecipe(recipe, body)
                && !tryEncodeKubeJsRecipe(recipeId, body)
                && !tryEncodeForgeSerializerToJson(serializer, recipe, body)
                && !tryEncodeRecipeClassCodec(recipe, body)
                && !tryEncodeViaCodec(serializer, recipe, body)
                && !tryInvokeSerializerToJson(serializer, recipe, body)) {
            body.addProperty("_runtimeStub", true);
            body.addProperty("_note", "serializer has no codec/toJson; id preserved for index lookup");
        }

        JsonObject out = new JsonObject();
        out.addProperty("type", typeId.toString());
        String source = body.has("_source") ? body.get("_source").getAsString() : "runtime";
        out.addProperty("_source", source);
        for (var entry : body.entrySet()) {
            if (!"type".equals(entry.getKey()) && !"_source".equals(entry.getKey())) {
                out.add(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    static boolean isRuntimeStub(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return false;
        }
        JsonObject obj = element.getAsJsonObject();
        return obj.has("_runtimeStub") && obj.get("_runtimeStub").getAsBoolean();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean tryEncodeViaCodec(RecipeSerializer<?> serializer, Recipe<?> recipe, JsonObject target) {
        Codec codec = resolveSerializerCodec(serializer);
        if (codec == null) {
            return false;
        }
        try {
            JsonElement encoded = (JsonElement) codec.encodeStart(JsonOps.INSTANCE, recipe)
                    .getOrThrow(false, msg -> new IllegalStateException(String.valueOf(msg)));
            mergeJsonInto(encoded, target);
            return true;
        } catch (Exception e) {
            LOGGER.trace("[recipes] codec encode failed for {}: {}", recipe.getId(), e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("rawtypes")
    private static Codec resolveSerializerCodec(RecipeSerializer<?> serializer) {
        Codec fromMethods = resolveCodecFromMethods(serializer.getClass(), serializer);
        if (fromMethods != null) {
            return fromMethods;
        }
        for (Class<?> type = serializer.getClass().getSuperclass(); type != null; type = type.getSuperclass()) {
            fromMethods = resolveCodecFromMethods(type, serializer);
            if (fromMethods != null) {
                return fromMethods;
            }
        }
        for (Field field : serializer.getClass().getFields()) {
            Codec c = codecFromField(field, serializer);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    private static Codec resolveCodecFromMethods(Class<?> type, Object target) {
        try {
            for (Method method : type.getMethods()) {
                if (!"codec".equals(method.getName()) || method.getParameterCount() != 0) {
                    continue;
                }
                Class<?> ret = method.getReturnType();
                Object value = java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        ? method.invoke(null)
                        : method.invoke(target);
                Codec codec = codecFromValue(value);
                if (codec != null) {
                    return codec;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    private static Codec codecFromField(Field field, Object serializer) {
        try {
            Object value = java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    ? field.get(null)
                    : field.get(serializer);
            return codecFromValue(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("rawtypes")
    private static Codec codecFromValue(Object value) {
        if (value instanceof Codec c) {
            return c;
        }
        if (value instanceof MapCodec<?> mapCodec) {
            return mapCodec.codec();
        }
        return null;
    }

    private static boolean tryEncodeKubeJsRecipe(ResourceLocation recipeId, JsonObject target) {
        JsonObject cached = KubeJsRecipeJsonCache.get(recipeId);
        if (cached == null) {
            return false;
        }
        mergeJsonInto(cached.deepCopy(), target);
        target.addProperty("_source", "kubejs");
        return true;
    }

    private static boolean tryEncodeForgeSerializerToJson(
            RecipeSerializer<?> serializer,
            Recipe<?> recipe,
            JsonObject target) {
        for (Method method : serializer.getClass().getMethods()) {
            if (!"toJson".equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            if (!JsonObject.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            try {
                Object result = method.invoke(serializer, recipe);
                if (result instanceof JsonObject json) {
                    mergeJsonInto(json, target);
                    return true;
                }
            } catch (Exception ignored) {
                // try next overload
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean tryEncodeRecipeClassCodec(Recipe<?> recipe, JsonObject target) {
        for (Class<?> type = recipe.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!"CODEC".equals(field.getName())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = java.lang.reflect.Modifier.isStatic(field.getModifiers())
                            ? field.get(null)
                            : field.get(recipe);
                    Codec codec = codecFromValue(value);
                    if (codec == null) {
                        continue;
                    }
                    JsonElement encoded = (JsonElement) codec.encodeStart(JsonOps.INSTANCE, recipe)
                            .getOrThrow(false, msg -> new IllegalStateException(String.valueOf(msg)));
                    mergeJsonInto(encoded, target);
                    return true;
                } catch (Exception e) {
                    LOGGER.trace("[recipes] recipe class CODEC encode failed for {} ({}): {}",
                            recipe.getId(), type.getSimpleName(), e.getMessage());
                }
            }
        }
        return false;
    }

    private static void mergeJsonInto(JsonElement encoded, JsonObject target) {
        if (encoded.isJsonObject()) {
            JsonObject obj = encoded.getAsJsonObject();
            for (var entry : obj.entrySet()) {
                target.add(entry.getKey(), entry.getValue());
            }
        } else {
            target.add("value", encoded);
        }
    }

    private static boolean tryInvokeSerializerToJson(RecipeSerializer<?> serializer, Recipe<?> recipe, JsonObject target) {
        for (Method method : serializer.getClass().getMethods()) {
            if (!"toJson".equals(method.getName()) || method.getParameterCount() != 2) {
                continue;
            }
            if (!JsonObject.class.isAssignableFrom(method.getParameterTypes()[1])) {
                continue;
            }
            try {
                method.invoke(serializer, recipe, target);
                return true;
            } catch (Exception ignored) {
                // try next overload
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean tryEncodeGtRecipe(Recipe<?> recipe, JsonObject target) {
        if (!GT_RECIPE_CLASS.equals(recipe.getClass().getName())) {
            return false;
        }
        try {
            Class<?> serializerClass = Class.forName(GT_RECIPE_SERIALIZER_CLASS);
            Field codecField = serializerClass.getDeclaredField("CODEC");
            Codec codec = (Codec) codecField.get(null);
            JsonElement encoded = (JsonElement) codec.encodeStart(JsonOps.INSTANCE, recipe)
                    .getOrThrow(false, msg -> new IllegalStateException(String.valueOf(msg)));
            mergeJsonInto(encoded, target);
            return true;
        } catch (Exception e) {
            LOGGER.trace("[recipes] GTRecipe codec encode failed for {}: {}", recipe.getId(), e.getMessage());
            return false;
        }
    }
}
