package io.github.tfgcn.fieldguide.render;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;

/**
 * 测试玻璃纹理检测功能
 */
public class GlassDetectionTest {

    private BaseModelBuilder builder;

    @BeforeEach
    void setUp() {
        // 创建一个简单的 BaseModelBuilder 实例用于测试
        // 不需要完整的 AssetLoader 来测试玻璃检测方法
        builder = new BaseModelBuilder(null) {
            @Override
            protected String getTexture(java.util.Map<String, String> map, String id) {
                return "";
            }
        };
    }

    @Test
    void testGlassDetection() throws Exception {
        // 使用反射获取私有方法
        Method isGlassTextureMethod = BaseModelBuilder.class.getDeclaredMethod("isGlassTexture", String.class);
        isGlassTextureMethod.setAccessible(true);

        // 测试普通玻璃纹理
        assertTrue((Boolean) isGlassTextureMethod.invoke(builder, "minecraft:block/glass"));
        assertTrue((Boolean) isGlassTextureMethod.invoke(builder, "minecraft:block/stained_glass"));
        assertTrue((Boolean) isGlassTextureMethod.invoke(builder, "minecraft:block/tinted_glass"));
        assertTrue((Boolean) isGlassTextureMethod.invoke(builder, "minecraft:block/glass_pane"));
        assertTrue((Boolean) isGlassTextureMethod.invoke(builder, "minecraft:block/stained_glass_pane"));

        // 测试 TFC 玻璃纹理
        assertTrue((Boolean) isGlassTextureMethod.invoke(builder, "tfc:block/glass"));
        assertTrue((Boolean) isGlassTextureMethod.invoke(builder, "tfc:glass/clear"));
        assertTrue((Boolean) isGlassTextureMethod.invoke(builder, "assets/tfc/textures/block/glass.png"));

        // 测试非玻璃纹理
        assertFalse((Boolean) isGlassTextureMethod.invoke(builder, "minecraft:block/stone"));
        assertFalse((Boolean) isGlassTextureMethod.invoke(builder, "minecraft:block/dirt"));
        assertFalse((Boolean) isGlassTextureMethod.invoke(builder, "minecraft:block/wood"));
        assertFalse((Boolean) isGlassTextureMethod.invoke(builder, (String) null));

        // 测试大小写不敏感
        assertTrue((Boolean) isGlassTextureMethod.invoke(builder, "Minecraft:block/GLASS"));
        assertTrue((Boolean) isGlassTextureMethod.invoke(builder, "TFC:block/Glass"));
    }

    @Test
    void testGlassDetectionWithPaths() throws Exception {
        Method isGlassTextureMethod = BaseModelBuilder.class.getDeclaredMethod("isGlassTexture", String.class);
        isGlassTextureMethod.setAccessible(true);

        // 测试完整路径
        assertTrue((Boolean) isGlassTextureMethod.invoke(builder, 
            "assets/minecraft/textures/block/glass.png"));
        assertTrue((Boolean) isGlassTextureMethod.invoke(builder, 
            "assets/tfc/textures/block/glass/clear.png"));

        // 测试带 overlay 的玻璃纹理
        assertTrue((Boolean) isGlassTextureMethod.invoke(builder, 
            "minecraft:block/stained_glass_white_overlay"));
    }
}