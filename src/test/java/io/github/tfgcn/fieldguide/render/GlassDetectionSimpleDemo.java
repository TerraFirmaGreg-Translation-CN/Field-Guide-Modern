package io.github.tfgcn.fieldguide.render;

/**
 * 玻璃检测功能简化演示（无依赖）
 */
public class GlassDetectionSimpleDemo {

    /**
     * 简化版的玻璃检测方法
     */
    private static boolean isGlassTexture(String texturePath) {
        if (texturePath == null) {
            return false;
        }
        
        // 转换为小写进行匹配
        String lowerPath = texturePath.toLowerCase();
        
        // 检测常见的玻璃纹理关键词
        return lowerPath.contains("glass") || 
               lowerPath.contains("stained_glass") ||
               lowerPath.contains("tinted_glass") ||
               lowerPath.contains("glass_pane") ||
               lowerPath.contains("stained_glass_pane") ||
               // TFC 相关玻璃纹理
               lowerPath.contains("tfc:glass") ||
               lowerPath.contains("tfc/glass");
    }

    public static void main(String[] args) {
        String[] testTextures = {
            // 玻璃纹理 - 应该返回 true
            "minecraft:block/glass",
            "minecraft:block/stained_glass",
            "minecraft:block/stained_glass_white",
            "minecraft:block/tinted_glass",
            "minecraft:block/glass_pane",
            "minecraft:block/stained_glass_pane",
            "tfc:block/glass",
            "tfc:block/glass/clear",
            "assets/minecraft/textures/block/glass.png",
            "assets/tfc/textures/block/glass/clear.png",
            "minecraft:block/stained_glass_white_overlay",
            "Minecraft:block/GLASS",  // 大小写测试
            
            // 非玻璃纹理 - 应该返回 false
            "minecraft:block/stone",
            "minecraft:block/dirt",
            "minecraft:block/wood",
            "minecraft:block/iron_block",
            "tfc:block/rock/granite",
            "assets/minecraft/textures/block/stone.png",
            null
        };

        System.out.println("=== 玻璃纹理检测演示 ===\n");
        
        for (String texture : testTextures) {
            boolean isGlass = isGlassTexture(texture);
            String result = isGlass ? "✓ 玻璃" : "✗ 非玻璃";
            String display = texture != null ? texture : "null";
            
            System.out.printf("%-50s %s%n", display, result);
        }

        System.out.println("\n=== 演示完成 ===");
    }
}