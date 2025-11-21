package io.github.tfgcn.fieldguide.render;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * 玻璃检测功能演示
 */
@Slf4j
public class GlassDetectionDemo {

    public static void main(String[] args) {
        try {
            // 创建一个简单的 BaseModelBuilder 实例
            BaseModelBuilder builder = new BaseModelBuilder(null) {
                @Override
                protected String getTexture(java.util.Map<String, String> map, String id) {
                    return "";
                }
            };

            // 获取玻璃检测方法
            Method isGlassTextureMethod = BaseModelBuilder.class.getDeclaredMethod("isGlassTexture", String.class);
            isGlassTextureMethod.setAccessible(true);

            // 测试各种纹理路径
            List<String> testTextures = Arrays.asList(
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
            );

            System.out.println("=== 玻璃纹理检测演示 ===\n");
            
            for (String texture : testTextures) {
                boolean isGlass = (Boolean) isGlassTextureMethod.invoke(builder, texture);
                String result = isGlass ? "✓ 玻璃" : "✗ 非玻璃";
                String display = texture != null ? texture : "null";
                
                System.out.printf("%-50s %s%n", display, result);
            }

            System.out.println("\n=== 演示完成 ===");
            
        } catch (Exception e) {
            log.error("演示过程中出错", e);
        }
    }
}