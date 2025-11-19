package io.github.tfgcn.fieldguide.render.components;

import io.github.tfgcn.fieldguide.Context;
import io.github.tfgcn.fieldguide.asset.ItemImageResult;
import io.github.tfgcn.fieldguide.asset.ItemStackResult;
import io.github.tfgcn.fieldguide.exception.InternalException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class CraftingRecipeFormatter {

    /**
     * 格式化合成配方
     */
    public static void formatCraftingRecipe(Context context, List<String> buffer, String identifier) {
        Map<String, Object> recipe = context.getLoader().loadRecipe(identifier);
        formatCraftingRecipeFromData(context, buffer, identifier, recipe);
    }
    
    /**
     * 从数据格式化合成配方
     */
    public static void formatCraftingRecipeFromData(Context context, List<String> buffer, 
                                                   String identifier, Map<String, Object> data) {
        String recipeType = (String) data.get("type");
        CraftingRecipe recipe;
        
        switch (recipeType) {
            case "minecraft:crafting_shaped":
                recipe = parseShapedRecipe(context, data);
                break;
                
            case "minecraft:crafting_shapeless": {
                recipe = parseShapelessRecipe(context, data);
                break;
            }
            case "waterflasks:heal_flask": {
                Map<String, Object> innerRecipe = (Map<String, Object>) data.get("recipe");
                String type = (String) innerRecipe.get("type");
                if ("minecraft:crafting_shaped".equals(type)) {
                    recipe = parseShapedRecipe(context, innerRecipe);
                } else if ("minecraft:crafting_shapless".equals(type)) {
                    recipe = parseShapelessRecipe(context, innerRecipe);
                } else {
                    throw new InternalException("Unsupported recipe: " + type + " of " + identifier);
                }
                break;
            }
            case "tfc:damage_inputs_shaped_crafting":
            case "tfc:damage_inputs_shapeless_crafting":
            case "tfc:extra_products_shapeless_crafting":
            case "tfc:no_remainder_shapeless_crafting": {
                Map<String, Object> innerRecipe = (Map<String, Object>) data.get("recipe");
                formatCraftingRecipeFromData(context, buffer, identifier, innerRecipe);
                return;
            }
            case "tfc:advanced_shaped_crafting": {
                data.put("type", "minecraft:crafting_shaped");
                Object result = data.get("result");
                Object stack = anyOf(result, "stack", "id");
                if (stack == null) {
                    throw new InternalException("Advanced shaped crafting with complex modifiers: '" + data.get("result") + "'");
                }
                data.put("result", stack); // 丢弃修饰符
                formatCraftingRecipeFromData(context, buffer, identifier, data);
                return;
            }
            case "tfc:advanced_shapeless_crafting": {
                data.put("type", "minecraft:crafting_shapeless");
                Object result2 = data.get("result");
                Object stack2 = anyOf(result2, "stack", "id");
                if (stack2 == null) {
                    throw new InternalException("Advanced shapeless crafting with complex modifiers: '" + data.get("result") + "'");
                }
                data.put("result", stack2); // 丢弃修饰符
                formatCraftingRecipeFromData(context, buffer, identifier, data);
                return;
            }
            default:
                throw new RuntimeException("Unknown crafting recipe type: " + recipeType + " for recipe " + identifier);
        }

        for (int i = 0; i < recipe.grid.length; i++) {
            Object key = recipe.grid[i];
            if (key != null) {
                recipe.grid[i] = formatIngredient(context, key);
            }
        }

        buffer.add(String.format("""
            <div class="d-flex align-items-center justify-content-center">
                <div class="crafting-recipe">
                    <img src="../../_images/crafting_%s.png" />
            """, recipe.shapeless ? "shapeless" : "shaped"));

        // 添加网格物品
        for (int i = 0; i < recipe.grid.length; i++) {
            Object key = recipe.grid[i];
            if (key != null) {
                ItemImageResult ingredient = (ItemImageResult) key;
                int x = i % 3;
                int y = i / 3;
                buffer.add(String.format("""
                    <div class="crafting-recipe-item crafting-recipe-pos-%d-%d">
                        <span href="#" data-bs-toggle="tooltip" title="%s" class="crafting-recipe-item-tooltip"></span>
                        <img class="recipe-item" src="%s" />
                    </div>
                    """, x, y, ingredient.getName(), ingredient.getPath()));
            }
        }

        // 添加输出物品
        ItemStackResult output = recipe.output;
        buffer.add(String.format("""
                    <div class="crafting-recipe-item crafting-recipe-pos-out">
                        <span href="#" data-bs-toggle="tooltip" title="%s" class="crafting-recipe-item-tooltip"></span>
                        %s
                        <img class="recipe-item" src="%s" />
                    </div>
                </div>
            </div>
            """,
            output.name,
            formatCount(output.count),
            output.path
        ));
    }

    /**
     * 从多个键中获取第一个存在的值
     */
    public static Object anyOf(Object data, String... keys) {
        if (data instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) data;
            for (String key : keys) {
                if (map.containsKey(key)) {
                    return map.get(key);
                }
            }
        }
        return null;
    }

    /**
     * 解析有序合成配方
     */
    private static CraftingRecipe parseShapedRecipe(Context context, Map<String, Object> data) {
        CraftingRecipe recipe = new CraftingRecipe();
        List<String> pattern = (List<String>) data.get("pattern");
        Map<String, Object> keyMap = (Map<String, Object>) data.get("key");
        
        for (int y = 0; y < pattern.size(); y++) {
            String row = pattern.get(y);
            for (int x = 0; x < row.length(); x++) {
                char keyChar = row.charAt(x);
                if (keyChar != ' ') {
                    recipe.grid[x + 3 * y] = keyMap.get(String.valueOf(keyChar));
                }
            }
        }
        
        recipe.output = formatItemStack(context, data.get("result"));
        recipe.shapeless = false;
        return recipe;
    }
    
    /**
     * 解析无序合成配方
     */
    private static CraftingRecipe parseShapelessRecipe(Context context, Map<String, Object> data) {
        CraftingRecipe recipe = new CraftingRecipe();
        List<Object> ingredients = (List<Object>) data.get("ingredients");
        
        for (int i = 0; i < ingredients.size(); i++) {
            recipe.grid[i] = ingredients.get(i);
        }
        
        recipe.output = formatItemStack(context, data.get("result"));
        recipe.shapeless = true;
        return recipe;
    }
    
    /**
     * 格式化成分
     */
    public static ItemImageResult formatIngredient(Context context, Object data) {
        if (data instanceof Map) {
            Map<String, Object> mapData = (Map<String, Object>) data;
            
            if (mapData.containsKey("item")) {
                return context.getTextureRenderer().getItemImage((String) mapData.get("item"), true);
            } else if (mapData.containsKey("tag")) {
                return context.getTextureRenderer().getItemImage("#" + mapData.get("tag"), true);
            } else if (mapData.containsKey("type")) {
                String type = (String) mapData.get("type");
                switch (type) {
                    case "tfc:has_trait": {// FIXME 不知道对不对。这是 firmalife:food/pineapple firmalife:dried
                        return formatIngredient(context, mapData.get("ingredient"));
                    }
                    case "tfc:lacks_trait": {// FIXME 不知道对不对。这是 casting_channel 做巧克力的配方。
                        return formatIngredient(context, mapData.get("ingredient"));
                    }
                    case "tfc:not_rotten":
                        return formatIngredient(context, mapData.get("ingredient"));
                    case "tfc:fluid_item":
                        Map<String, Object> fluidIngredient = (Map<String, Object>) mapData.get("fluid_ingredient");
                        Map<String, Object> ingredient = (Map<String, Object>) fluidIngredient.get("ingredient");
                        if (!"minecraft:water".equals(ingredient.get("ingredient"))) {
                            throw new RuntimeException("Unknown `tfc:fluid_item` ingredient: '" + data + "'");
                        }
                        return context.getTextureRenderer().getItemImage("minecraft:water_bucket", true);
                    case "tfc:fluid_content":
                        Map<String, Object> fluid = (Map<String, Object>) mapData.get("fluid");
                        if (!"minecraft:water".equals(fluid.get("fluid"))) {
                            throw new RuntimeException("Unknown `tfc:fluid_content` ingredient: '" + data + "'");
                        }
                        return context.getTextureRenderer().getItemImage("minecraft:water_bucket", true);
                    case "tfc:and":
                        List<Map<String, Object>> children = (List<Map<String, Object>>) mapData.get("children");
                        StringBuilder csvString = new StringBuilder();
                        for (Map<String, Object> child : children) {
                            if (child.containsKey("item")) {
                                if (!csvString.isEmpty()) {
                                    csvString.append(",");
                                }
                                csvString.append(child.get("item"));
                            }
                        }
                        return context.getTextureRenderer().getItemImage(csvString.toString(), true);
                    default:
                        log.info("Unknown ingredient type: {}", type);
                }
            }
        } else if (data instanceof List) {
            List<Object> listData = (List<Object>) data;
            StringBuilder csvString = new StringBuilder();
            for (Object item : listData) {
                if (item instanceof Map) {
                    Map<String, Object> itemMap = (Map<String, Object>) item;
                    if (itemMap.containsKey("item")) {
                        if (!csvString.isEmpty()) {
                            csvString.append(",");
                        }
                        csvString.append(itemMap.get("item"));
                    }
                }
            }
            return context.getTextureRenderer().getItemImage(csvString.toString(), true);
        }
        
        throw new RuntimeException("Unsupported ingredient: " + data);
    }
    
    /**
     * 格式化带数量的成分
     */
    public static SizedIngredientResult formatSizedIngredient(Context context, Object data) {
        ItemImageResult ingredient = formatIngredient(context, data);
        int count = 1;
        if (data instanceof Map) {
            Map<String, Object> mapData = (Map<String, Object>) data;
            if (mapData.containsKey("count")) {
                count = ((Number) mapData.get("count")).intValue();
            }
        }
        return new SizedIngredientResult(ingredient, count);
    }
    
    /**
     * 格式化物品堆
     */
    public static ItemStackResult formatItemStack(Context context, Object data) {
        if (data instanceof Map) {
            Map<String, Object> mapData = (Map<String, Object>) data;
            if (mapData.containsKey("modifiers") && mapData.containsKey("stack")) {
                return formatItemStack(context, mapData.get("stack")); // 丢弃修饰符
            }
            
            String itemId = null;
            if (mapData.containsKey("item")) {
                itemId = (String) mapData.get("item");
            } else if (mapData.containsKey("id")) {
                itemId = (String) mapData.get("id");
            }
            
            if (itemId != null) {
                ItemImageResult itemImage = context.getTextureRenderer().getItemImage(itemId, true);
                int count = mapData.containsKey("count") ? ((Number) mapData.get("count")).intValue() : 1;
                return new ItemStackResult(itemImage.getPath(), itemImage.getName(), count);
            }
        }
        
        // 默认返回占位符
        return new ItemStackResult("../../_images/placeholder_64.png", null, 1);
    }
    
    /**
     * 格式化数量显示
     */
    public static String formatCount(int count) {
        return count > 1 ? 
            String.format("<p class=\"crafting-recipe-item-count\">%d</p>", count) : 
            "";
    }
}

