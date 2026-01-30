package team.terrafirmgreg.fieldguide.data.recipe.adapter;

import com.google.gson.*;
import team.terrafirmgreg.fieldguide.data.recipe.Ingredient;
import team.terrafirmgreg.fieldguide.data.recipe.ingredient.*;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;

@Slf4j
public class IngredientDeserializer implements JsonDeserializer<Ingredient> {
    
    @Override
    public Ingredient deserialize(JsonElement json, Type typeOfT,
                                  JsonDeserializationContext context) throws JsonParseException {
        
        // 1. 处理简单字符串形式
        if (json.isJsonPrimitive()) {
            String value = json.getAsString();
            if (value.startsWith("#")) {
                TagIngredient tagIngredient = new TagIngredient();
                tagIngredient.setTag(value.substring(1));
                return tagIngredient;
            } else {
                ItemIngredient itemIngredient = new ItemIngredient();
                itemIngredient.setItem(value);
                return itemIngredient;
            }
        } else if (json.isJsonArray()) {
            return parseArrayIngredient(json.getAsJsonArray(), context);
        } else {

            JsonObject obj = json.getAsJsonObject();

            // 2. 处理复合成分类型
            if (obj.has("type")) {
                String type = obj.get("type").getAsString();
                return parseCompoundIngredient(type, obj, context);
            }

            // 3. 处理标准成分结构
            if (obj.has("item")) {
                ItemIngredient ingredient = new ItemIngredient();
                ingredient.setItem(obj.get("item").getAsString());
                if (obj.has("count")) {
                    ingredient.setCount(obj.get("count").getAsInt());
                }
                return ingredient;
            }

            if (obj.has("tag")) {
                TagIngredient ingredient = new TagIngredient();
                ingredient.setTag(obj.get("tag").getAsString());
                return ingredient;
            }

            if (obj.has("fluid_ingredient")) {
                // 流体物品成分
                return parseFluidItemIngredient(obj, context);
            }

            if (obj.has("fluid")) {
                // 流体成分
                return parseFluidIngredient(obj, context);
            }

            if (obj.has("ingredient") && obj.has("count")) {
                // 带数量的成分（常用于织布机配方）
                return parseCountedIngredient(obj, context);
            }

            // 4. 处理数组形式（成分列表）
            if (json.isJsonArray()) {
                return parseArrayIngredient(json.getAsJsonArray(), context);
            }
        }
        
        throw new JsonParseException("无法解析的成分格式: " + json);
    }
    
    private Ingredient parseCompoundIngredient(String type, JsonObject obj, JsonDeserializationContext context) {
        switch (type) {
            case "tfc:has_trait":
            case "tfc:lacks_trait":
                TraitIngredient traitIngredient = new TraitIngredient();
                traitIngredient.setType(type);
                traitIngredient.setTrait(obj.get("trait").getAsString());
                traitIngredient.setIngredient(context.deserialize(obj.get("ingredient"), Ingredient.class));
                return traitIngredient;
                
            case "tfc:not_rotten":
                NotRottenIngredient notRotten = new NotRottenIngredient();
                notRotten.setType(type);
                notRotten.setIngredient(context.deserialize(obj.get("ingredient"), Ingredient.class));
                return notRotten;
                
            case "tfc:and":
                ListIngredient list = new ListIngredient();
                list.setType(type);
                JsonArray children = obj.getAsJsonArray("children");
                for (JsonElement child : children) {
                    list.add(context.deserialize(child, Ingredient.class));
                }
                return list;
                
            case "tfc:fluid_item":
                return parseFluidItemIngredient(obj, context);
                
            case "tfc:fluid_content":
                return parseFluidIngredient(obj, context);
                
            default:
                throw new JsonParseException("未知的成分类型: " + type);
        }
    }
    
    private Ingredient parseFluidItemIngredient(JsonObject obj, JsonDeserializationContext context) {
        FluidItemIngredient fluidItem = new FluidItemIngredient();
        if (obj.has("type")) {
            fluidItem.setType(obj.get("type").getAsString());
        }
        JsonObject fluidIngredientObj = obj.getAsJsonObject("fluid_ingredient");
        FluidIngredient fluidIngredient = new FluidIngredient();
        
        if (fluidIngredientObj.has("ingredient")) {
            fluidIngredient.setIngredient(fluidIngredientObj.get("ingredient").getAsString());
        }
        if (fluidIngredientObj.has("amount")) {
            fluidIngredient.setAmount(fluidIngredientObj.get("amount").getAsInt());
        }
        
        fluidItem.setFluidIngredient(fluidIngredient);
        return fluidItem;
    }
    
    private Ingredient parseFluidIngredient(JsonObject obj, JsonDeserializationContext context) {
        // 这里简化处理，实际可能需要专门的 FluidIngredient 类
        FluidItemIngredient fluidItem = new FluidItemIngredient();
//        if (obj.has("type")) {
//            fluidItem.setType(obj.get("type").getAsString());
//        }

        FluidIngredient fluidIngredient = new FluidIngredient();
        
        JsonObject fluidObj = obj.getAsJsonObject("fluid");
        if (fluidObj.has("fluid")) {
            fluidIngredient.setIngredient(fluidObj.get("fluid").getAsString());
        }
        if (fluidObj.has("amount")) {
            fluidIngredient.setAmount(fluidObj.get("amount").getAsInt());
        }
        
        fluidItem.setFluidIngredient(fluidIngredient);
        return fluidItem;
    }
    
    private Ingredient parseCountedIngredient(JsonObject obj, JsonDeserializationContext context) {
        // 创建包装成分，保留数量信息
        JsonElement ingredientElement = obj.get("ingredient");
        Ingredient innerIngredient;
        if (ingredientElement.isJsonArray()) {
            innerIngredient = parseArrayIngredient(ingredientElement.getAsJsonArray(), context);
        } else {
            innerIngredient = context.deserialize(ingredientElement, Ingredient.class);
        }
        int count = obj.get("count").getAsInt();
        
        // 如果是 ItemIngredient，直接设置数量
        if (innerIngredient instanceof ItemIngredient) {
            ((ItemIngredient) innerIngredient).setCount(count);
            return innerIngredient;
        }
        
        // 其他类型可能需要特殊处理
        return innerIngredient;
    }
    
    private Ingredient parseArrayIngredient(JsonArray array, JsonDeserializationContext context) {
        // 将数组转换为 AND 成分
        ListIngredient list = new ListIngredient();
        for (JsonElement element : array) {
            list.add(context.deserialize(element, Ingredient.class));
        }
        return list;
    }
}