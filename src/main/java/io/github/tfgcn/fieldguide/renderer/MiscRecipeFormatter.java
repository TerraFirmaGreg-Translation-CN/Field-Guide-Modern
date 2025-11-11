package io.github.tfgcn.fieldguide.renderer;

import io.github.tfgcn.fieldguide.Context;
import io.github.tfgcn.fieldguide.asset.ItemImageResult;
import io.github.tfgcn.fieldguide.asset.ItemStackResult;

import java.util.*;
import java.util.stream.Collectors;

public class MiscRecipeFormatter {
    
    // 热量等级定义
    private static final List<HeatLevel> HEAT = Arrays.asList(
        new HeatLevel("warming", "gray", 80),
        new HeatLevel("hot", "gray", 210),
        new HeatLevel("very_hot", "gray", 480),
        new HeatLevel("faint_red", "dark-red", 580),
        new HeatLevel("dark_red", "dark-red", 730),
        new HeatLevel("bright_red", "red", 930),
        new HeatLevel("orange", "gold", 1100),
        new HeatLevel("yellow", "yellow", 1300),
        new HeatLevel("yellow_white", "yellow", 1400),
        new HeatLevel("white", "white", 1500),
        new HeatLevel("brilliant_white", "white", 1600)
    );
    
    // 玻璃加工物品映射
    private static final Map<String, String> GLASS_ITEMS = Map.ofEntries(
        Map.entry("saw", "tfc:gem_saw"),
        Map.entry("roll", "tfc:wool_cloth"),
        Map.entry("stretch", "tfc:blowpipe_with_glass"),
        Map.entry("blow", "tfc:blowpipe_with_glass"),
        Map.entry("table_pour", "tfc:blowpipe_with_glass"),
        Map.entry("basin_pour", "tfc:blowpipe_with_glass"),
        Map.entry("flatten", "tfc:paddle"),
        Map.entry("pinch", "tfc:jacks")
    );
    
    /**
     * 格式化杂项配方
     */
    public static void formatMiscRecipe(Context context, List<String> buffer, String identifier) {
        Map<String, Object> data = context.getLoader().loadRecipe(identifier);
        String recipeType = (String) data.get("type");
        
        switch (recipeType) {
            case "tfc:quern":
                formatMiscRecipeFromData(context, buffer, identifier, data);
                break;
                
            case "tfc:heating":
                formatMiscRecipeFromData(context, buffer, identifier, data, "result_item");
                TemperatureResult tempResult = getTemperature(context, ((Number) data.get("temperature")).intValue());
                buffer.add(String.format("""
                    <div style="text-align: center;" class="minecraft-text minecraft-%s">
                        <p>%s</p>
                    </div>
                    """, tempResult.cssClass, tempResult.tooltip));
                break;
                
            case "tfc:loom":
                handleLoomRecipe(context, buffer, identifier, data);
                break;
                
            case "tfc:anvil":
                handleAnvilRecipe(context, buffer, identifier, data);
                break;
                
            case "tfc:glassworking":
                formatGlassworkingRecipe(context, buffer, identifier, data);
                break;
                
            default:
                throw new RuntimeException("Cannot handle as a misc recipe: " + recipeType);
        }
    }
    
    /**
     * 处理织布机配方
     */
    private static void handleLoomRecipe(Context context, List<String> buffer, String identifier, Map<String, Object> data) {
        int count = 1;
        Object ingredient = null;
        
        if (data.containsKey("ingredient")) {
            Object ing = data.get("ingredient");
            if (ing instanceof Map) {
                Map<String, Object> ingMap = (Map<String, Object>) ing;
                if (data.containsKey("input_count")) {
                    // 1.18 格式
                    count = ((Number) data.get("input_count")).intValue();
                    ingredient = ing;
                } else if (ingMap.containsKey("count")) {
                    // 1.20 格式
                    count = ((Number) ingMap.get("count")).intValue();
                    ingredient = ing;
                } else if (ingMap.containsKey("ingredient") && ingMap.containsKey("count")) {
                    // 嵌套格式
                    ingredient = ingMap.get("ingredient");
                    count = ((Number) ingMap.get("count")).intValue();
                }
            }
        }
        
        if (ingredient != null) {
            formatMiscRecipeFromData(context, buffer, identifier, data, ingredient, count);
        } else {
            throw new RuntimeException("Unrecognized loom recipe format: " + data);
        }
    }
    
    /**
     * 处理铁砧配方
     */
    private static void handleAnvilRecipe(Context context, List<String> buffer, String identifier, Map<String, Object> data) {
        Object ingredient = null;
        
        if (data.containsKey("input")) {
            ingredient = data.get("input");
        } else if (data.containsKey("ingredient")) {
            ingredient = data.get("ingredient");
        }
        
        if (ingredient != null) {
            formatMiscRecipeFromData(context, buffer, identifier, data, ingredient, 1);
            String tooltip = getTier(context, ((Number) data.get("tier")).intValue());
            buffer.add(String.format("""
                <div style="text-align: center;" class="minecraft-text minecraft-gray">
                    <p>%s</p>
                </div>
                """, tooltip));
        } else {
            throw new RuntimeException("Unrecognized anvil recipe format: " + data);
        }
    }
    
    /**
     * 处理玻璃加工配方
     */
    private static void formatGlassworkingRecipe(Context context, List<String> buffer, String identifier, Map<String, Object> data) {
        formatMiscRecipeFromData(context, buffer, identifier, data, data.get("batch"), 1);
        buffer.add("<h4>Steps</h4><ol>");
        
        List<String> operations = (List<String>) data.get("operations");
        for (String key : operations) {
            String opName = context.translate("tfc.enum.glassoperation." + key);
            
            if (!GLASS_ITEMS.containsKey(key)) {
                throw new RuntimeException("Missing item for glass op: " + key);
            }
            
            String opItem = GLASS_ITEMS.get(key);
            try {
                ItemImageResult itemResult = context.getItemImage(opItem, false);
                buffer.add("<li>");
                context.formatTitleWithIcon(buffer, itemResult.getPath(), opName, (String)data.get( "title"), "p", itemResult.getName(), null);
                buffer.add("</li>");
                context.setItemsPassed(context.getItemsPassed() + 1);
            } catch (Exception e) {
                System.err.println("Warning: " + e.getMessage());
                buffer.add("<li><p>" + opName + "</p></li>");
            }
        }
        
        buffer.add("</ol>");
    }
    
    /**
     * 从数据格式化杂项配方
     */
    public static void formatMiscRecipeFromData(Context context, List<String> buffer, String identifier, 
                                               Map<String, Object> data) {
        formatMiscRecipeFromData(context, buffer, identifier, data, null, "result", 1);
    }
    
    public static void formatMiscRecipeFromData(Context context, List<String> buffer, String identifier,
                                               Map<String, Object> data, String resultField) {
        formatMiscRecipeFromData(context, buffer, identifier, data, null, resultField, 1);
    }
    
    public static void formatMiscRecipeFromData(Context context, List<String> buffer, String identifier,
                                               Map<String, Object> data, Object ingredient, int inCount) {
        formatMiscRecipeFromData(context, buffer, identifier, data, ingredient, "result", inCount);
    }
    
    public static void formatMiscRecipeFromData(Context context, List<String> buffer, String identifier,
                                               Map<String, Object> data, Object ingredient, String resultField, int inCount) {
        if (!data.containsKey(resultField)) {
            throw new RuntimeException("Missing '" + resultField + "' field for recipe: " + identifier);
        }
        
        if (ingredient == null) {
            ingredient = data.get("ingredient");
        }

        ItemImageResult inResult = CraftingRecipeFormatter.formatIngredient(context, ingredient);
        ItemStackResult outResult = CraftingRecipeFormatter.formatItemStack(context, data.get(resultField));
        
        buffer.add(String.format("""
            <div class="d-flex align-items-center justify-content-center">
                <div class="crafting-recipe">
                    <img src="../../_images/1to1.png" />
                    <div class="crafting-recipe-item misc-recipe-pos-in">
                        <span href="#" data-bs-toggle="tooltip" title="%s" class="crafting-recipe-item-tooltip"></span>
                        %s
                        <img class="recipe-item" src="%s" />
                    </div>
                    <div class="crafting-recipe-item misc-recipe-pos-out">
                        <span href="#" data-bs-toggle="tooltip" title="%s" class="crafting-recipe-item-tooltip"></span>
                        %s
                        <img class="recipe-item" src="%s" />
                    </div>
                </div>
            </div>
            """,
            inResult.getName(),
            CraftingRecipeFormatter.formatCount(inCount),
            inResult.getPath(),
            outResult.getName(),
            CraftingRecipeFormatter.formatCount(outResult.getCount()),
            outResult.getPath()
        ));
    }
    
    /**
     * 获取温度信息
     */
    public static TemperatureResult getTemperature(Context context, int temperature) {
        for (int i = 0; i < HEAT.size() - 1; i++) {
            HeatLevel current = HEAT.get(i);
            HeatLevel next = HEAT.get(i + 1);
            
            if (temperature <= current.value) {
                String tooltip = context.translate("tfc.enum.heat." + current.key);
                
                // 计算温度等级内的细分
                for (double t : Arrays.asList(0.2, 0.4, 0.6, 0.8)) {
                    if (temperature < current.value + (next.value - current.value) * t) {
                        tooltip += "*";
                    }
                }
                
                return new TemperatureResult(current.cssClass, tooltip);
            }
        }
        
        HeatLevel last = HEAT.get(HEAT.size() - 1);
        return new TemperatureResult(last.cssClass, context.translate("tfc.enum.heat." + last.key));
    }
    
    /**
     * 获取等级信息
     */
    public static String getTier(Context context, int tier) {
        String[] tierNames = {"0", "i", "ii", "iii", "iv", "v", "vi", "vii"};
        if (tier >= 0 && tier < tierNames.length) {
            return context.translate("tfc.enum.tier.tier_" + tierNames[tier]);
        }
        return context.translate("tfc.enum.tier.tier_0");
    }
    
    // 辅助类
    public static class HeatLevel {
        public final String key;
        public final String cssClass;
        public final int value;
        
        public HeatLevel(String key, String cssClass, int value) {
            this.key = key;
            this.cssClass = cssClass;
            this.value = value;
        }
    }
    
    public static class TemperatureResult {
        public final String cssClass;
        public final String tooltip;
        
        public TemperatureResult(String cssClass, String tooltip) {
            this.cssClass = cssClass;
            this.tooltip = tooltip;
        }
    }
}