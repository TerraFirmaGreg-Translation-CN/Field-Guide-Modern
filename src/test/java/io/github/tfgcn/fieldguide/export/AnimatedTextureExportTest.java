package io.github.tfgcn.fieldguide.export;

import io.github.tfgcn.fieldguide.asset.AssetLoader;
import io.github.tfgcn.fieldguide.render.BaseModelBuilder;
import io.github.tfgcn.fieldguide.render3d.animation.AnimatedTexture;
import io.github.tfgcn.fieldguide.render3d.animation.AnimatedMaterial;
import io.github.tfgcn.fieldguide.render3d.scene.Node;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

/**
 * 动画纹理导出测试
 */
@Slf4j
public class AnimatedTextureExportTest {
    
    public static void main(String[] args) {
        try {
            // 创建输出目录
            String outputDir = "output/animated_test";
            new File(outputDir).mkdirs();
            
            // 创建动画纹理图集（模拟16x128的动画，8帧）
            BufferedImage animationAtlas = createTestAnimationAtlas(16, 128, 8);
            ImageIO.write(animationAtlas, "png", new File(outputDir + "/test_animation.png"));
            
            // 创建资源加载器
            AssetLoader assetLoader = new AssetLoader(
                Paths.get("src/main/resources"),
                Paths.get(outputDir)
            );
            
            // 创建模型构建器
            BaseModelBuilder builder = new BaseModelBuilder(assetLoader);
            
            // 测试动画纹理检测
            boolean isAnimated = AnimatedTexture.isAnimationAtlas(animationAtlas);
            int frameCount = AnimatedTexture.calculateFrameCount(animationAtlas);
            
            log.info("动画纹理检测: {}", isAnimated);
            log.info("帧数: {}", frameCount);
            
            if (isAnimated) {
                // 提取所有帧
                var frames = AnimatedTexture.extractFrames(animationAtlas);
                log.info("提取了 {} 帧", frames.size());
                
                // 保存所有帧到文件
                for (int i = 0; i < frames.size(); i++) {
                    ImageIO.write(frames.get(i), "png", 
                        new File(outputDir + "/frame_" + i + ".png"));
                }
                
                // 创建动画纹理对象
                AnimatedTexture animatedTexture = new AnimatedTexture();
                animatedTexture.setTexturePath("test_animation");
                animatedTexture.setAnimated(true);
                animatedTexture.setFrameCount(frameCount);
                animatedTexture.setFrames(frames);
                animatedTexture.setFrameRate(10.0f);
                animatedTexture.setLoop(true);
                
                // 创建动画材质
                AnimatedMaterial animatedMaterial = new AnimatedMaterial(animatedTexture);
                
                log.info("创建了动画材质: 帧率={}, 循环={}", 
                    animatedTexture.getFrameRate(), 
                    animatedTexture.isLoop());
                
                // 测试材质更新
                long startTime = System.currentTimeMillis();
                for (int i = 0; i < 50; i++) {
                    long currentTime = System.currentTimeMillis() - startTime;
                    animatedMaterial.updateAnimation(currentTime);
                    
                    if (i % 10 == 0) {
                        log.info("时间={}ms, 当前帧={}", currentTime, 
                            animatedMaterial.getCurrentFrame());
                    }
                    
                    // 模拟10FPS延迟
                    Thread.sleep(100);
                }
            }
            
            log.info("动画测试完成！");
            
        } catch (Exception e) {
            log.error("测试失败", e);
        }
    }
    
    /**
     * 创建测试动画图集
     */
    private static BufferedImage createTestAnimationAtlas(int width, int height, int frameCount) {
        BufferedImage atlas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        
        int frameHeight = height / frameCount;
        
        for (int frame = 0; frame < frameCount; frame++) {
            for (int y = 0; y < frameHeight; y++) {
                for (int x = 0; x < width; x++) {
                    // 创建简单的颜色渐变动画
                    int brightness = (255 * frame) / frameCount;
                    int color = (brightness << 16) | (brightness << 8) | brightness | 0xFF000000;
                    
                    // 添加一些图案
                    if ((x + y + frame) % 4 < 2) {
                        color = 0xFF000000; // 黑色
                    }
                    
                    atlas.setRGB(x, frame * frameHeight + y, color);
                }
            }
        }
        
        return atlas;
    }
}