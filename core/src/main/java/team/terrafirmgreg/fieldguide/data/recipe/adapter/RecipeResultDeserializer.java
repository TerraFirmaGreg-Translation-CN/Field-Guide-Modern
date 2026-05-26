package team.terrafirmgreg.fieldguide.data.recipe.adapter;

import com.google.gson.*;
import team.terrafirmgreg.fieldguide.data.recipe.RecipeResult;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class RecipeResultDeserializer implements JsonDeserializer<RecipeResult> {
    
    @Override
    public RecipeResult deserialize(JsonElement json, Type typeOfT,
                                    JsonDeserializationContext context) throws JsonParseException {
        RecipeResult result = new RecipeResult();
        
        if (json.isJsonPrimitive()) {
            // 简单字符串形式，如 "minecraft:stick"
            String itemId = json.getAsString();
            result.setItem(itemId);
            return result;
        }
        
        JsonObject obj = json.getAsJsonObject();
        
        // 处理复杂的结果结构
        if (obj.has("stack")) {
            // 嵌套 stack 结构
            RecipeResult.Stack stack = context.deserialize(obj.get("stack"), RecipeResult.Stack.class);
            result.setStack(stack);
            
            // 复制修饰符
            if (obj.has("modifiers")) {
                List<Object> modifiers = parseModifiers(obj.get("modifiers"));
                result.setModifiers(modifiers);
            }
        } else {
            // 标准结果结构
            if (obj.has("item")) {
                result.setItem(obj.get("item").getAsString());
            }
            if (obj.has("tag")) {
                result.setTag(obj.get("tag").getAsString());
            }
            if (obj.has("count")) {
                result.setCount(obj.get("count").getAsInt());
            }
            if (obj.has("modifiers")) {
                List<Object> modifiers = parseModifiers(obj.get("modifiers"));
                result.setModifiers(modifiers);
            }
        }
        
        return result;
    }
    
    private List<Object> parseModifiers(JsonElement modifiersElement) {
        List<Object> modifiers = new ArrayList<>();
        
        if (modifiersElement.isJsonArray()) {
            JsonArray array = modifiersElement.getAsJsonArray();
            for (JsonElement element : array) {
                if (element.isJsonPrimitive()) {
                    modifiers.add(element.getAsString());
                } else if (element.isJsonObject()) {
                    // 对象修饰符，如 {"type": "tfc:add_trait", "trait": "firmalife:dried"}
                    modifiers.add(element.getAsJsonObject()); // 或者进一步解析为 Map
                }
            }
        }
        
        return modifiers;
    }
}