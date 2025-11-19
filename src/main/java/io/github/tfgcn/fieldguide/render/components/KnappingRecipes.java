package io.github.tfgcn.fieldguide.render.components;

import io.github.tfgcn.fieldguide.Context;
import io.github.tfgcn.fieldguide.asset.AssetKey;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.tfgcn.fieldguide.render.TextureRenderer.resizeImage;

/**
 * 敲击配方格式化工具
 */
public class KnappingRecipes {
    
    // 敲击类型常量
    public static final List<KnappingType> KNAPPING_TYPES = Arrays.asList(
        new KnappingType("tfc:rock_knapping", "tfc:rock", "tfc:textures/gui/knapping/rock/loose/granite.png", null),
        new KnappingType("tfc:clay_knapping", "tfc:clay", "tfc:textures/gui/knapping/clay_ball.png", "tfc:textures/gui/knapping/clay_ball_disabled.png"),
        new KnappingType("tfc:fire_clay_knapping", "tfc:fire_clay", "tfc:textures/gui/knapping/fire_clay.png", "tfc:textures/gui/knapping/fire_clay_disabled.png"),
        new KnappingType("tfc:leather_knapping", "tfc:leather", "tfc:textures/gui/knapping/leather.png", null),
        new KnappingType(null, "bsa:bone", "tfc:textures/gui/knapping/bone.png", null),
        new KnappingType(null, "bsa:sherd", "tfc:textures/gui/knapping/ceramic/sherd/unfired/blank.png", "tfc:textures/gui/knapping/ceramic/sherd/unfired/blank_disabled.png")
    );
    
    public static final String KNAPPING_RECIPE_OUTLINE = "tfc:textures/gui/book/icons.png";
    private static final Map<String, KnappingRecipe> KNAPPING_RECIPE_CACHE = new HashMap<>();
    
    /**
     * 格式化敲击配方
     * @param context 上下文对象
     * @param recipeId 配方ID
     * @return 包含配方ID和图片路径的数组
     */
    public static KnappingRecipe formatKnappingRecipe(Context context, String recipeId) {
        if (KNAPPING_RECIPE_CACHE.containsKey(recipeId)) {
            return KNAPPING_RECIPE_CACHE.get(recipeId);
        }
        
        Map<String, Object> recipeData = context.getLoader().loadRecipe(recipeId);
        BufferedImage img = new BufferedImage(90, 90, BufferedImage.TYPE_INT_ARGB);

        // 1.18版本使用'type'字段表示敲击类型
        // 1.20版本'type'字段仅为'tfc:knapping'，使用'knapping_type'字段表示具体类型
        KnappingType typeData;
        if (recipeData.containsKey("knapping_type")) {
            String knappingType = (String) recipeData.get("knapping_type");
            typeData = KNAPPING_TYPES.stream()
                .filter(t -> knappingType.equals(t.type_1_20()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Unknown knapping type: " + knappingType));
        } else {
            String type = (String) recipeData.get("type");
            typeData = KNAPPING_TYPES.stream()
                .filter(t -> type.equals(t.type_1_18()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Unknown knapping type: " + type));
        }
        
        BufferedImage low = null;
        BufferedImage hi = null;
        
        if (typeData.low() != null) {
            low = context.getLoader().loadTexture(typeData.low());
            if (low.getWidth() != 16 || low.getHeight() != 16) {
                low = resizeImage(low, 16, 16);
            }
        }
        if (typeData.hi() != null) {
            hi = context.getLoader().loadTexture(typeData.hi());
            if (hi.getWidth() != 16 || hi.getHeight() != 16) {
                hi = resizeImage(hi, 16, 16);
            }
        }
        
        // 图案
        List<String> pattern = (List<String>) recipeData.get("pattern");
        boolean outsideSlot = recipeData.containsKey("outside_slot_required") ? 
            (Boolean) recipeData.get("outside_slot_required") : true;
        
        // 如果图案在任何方向上小于5格宽，我们偏移它以使其居中显示，向下取整
        int offsetY = (5 - pattern.size()) / 2;
        int offsetX = (5 - pattern.getFirst().length()) / 2;
        
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                if (0 <= y - offsetY && y - offsetY < pattern.size() && 
                    0 <= x - offsetX && x - offsetX < pattern.get(y - offsetY).length()) {
                    // 在边界内
                    char patternChar = pattern.get(y - offsetY).charAt(x - offsetX);
                    BufferedImage tile = (patternChar == ' ') ? hi : low;
                    if (tile != null) {
                        // 在Java中绘制图片到指定位置
                        img.getGraphics().drawImage(tile, 5 + 16 * x, 5 + 16 * y, null);
                    }
                } else {
                    // 超出边界
                    BufferedImage tile = outsideSlot ? low : hi;
                    if (tile != null) {
                        img.getGraphics().drawImage(tile, 5 + 16 * x, 5 + 16 * y, null);
                    }
                }
            }
        }

        context.nextId("image");// counting images
        AssetKey assetKey = new AssetKey(recipeId, "textures/recipes", "assets", ".png");
        String path = context.getTextureRenderer().saveImage(assetKey.getResourcePath(), img);
        KnappingRecipe result = new KnappingRecipe(recipeId, path);
        KNAPPING_RECIPE_CACHE.put(recipeId, result);
        return result;
    }
}