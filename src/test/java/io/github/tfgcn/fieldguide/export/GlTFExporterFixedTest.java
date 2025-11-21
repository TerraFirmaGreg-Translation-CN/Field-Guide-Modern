package io.github.tfgcn.fieldguide.export;

import io.github.tfgcn.fieldguide.export.GlTFExporter;
import io.github.tfgcn.fieldguide.render3d.scene.Node;
import io.github.tfgcn.fieldguide.render3d.material.Material;
import io.github.tfgcn.fieldguide.render3d.material.RenderState;
import io.github.tfgcn.fieldguide.render3d.material.Texture;
import io.github.tfgcn.fieldguide.render3d.animation.AnimatedMaterial;
import io.github.tfgcn.fieldguide.render3d.animation.AnimatedTexture;


import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Paths;

/**
 * 测试修复后的 GlTF 导出器
 */
public class GlTFExporterFixedTest {

    public static void main(String[] args) {
        try {
            GlTFExporter exporter = new GlTFExporter();
            
            // 创建一个简单的节点树
            Node root = new Node();
            
            // 创建静态材质
            Material staticMaterial = createStaticMaterial();
            
            // 创建动画材质
            Material animatedMaterial = createAnimatedMaterial();
            
            // 测试导出
            File outputFile = new File("output/test_fixed_export.glb");
            outputFile.getParentFile().mkdirs();
            
            // 注意：这里我们创建一个简单的节点用于测试
            // 实际使用中，需要创建包含几何体的完整场景
            System.out.println("开始测试 glTF 导出...");
            
            // 创建一个测试节点（这里简化测试）
            Node testNode = new Node();
            root.attachChild(testNode);
            
            // 导出测试
            exporter.export(root, outputFile.toString());
            
            System.out.println("导出完成: " + outputFile.getAbsolutePath());
            
            // 验证文件存在
            if (outputFile.exists()) {
                long fileSize = outputFile.length();
                System.out.println("导出文件大小: " + fileSize + " bytes");
                
                if (fileSize > 0) {
                    System.out.println("✅ glTF 导出测试成功！");
                } else {
                    System.err.println("❌ 导出文件为空");
                }
            } else {
                System.err.println("❌ 导出文件不存在");
            }
            
        } catch (Exception e) {
            System.err.println("测试失败");
            e.printStackTrace();
        }
    }
    
    /**
     * 创建静态材质
     */
    private static Material createStaticMaterial() {
        Material material = new Material();
        material.getRenderState().setAlphaTest(true);
        material.getRenderState().setAlphaFalloff(0.1f);
        material.getRenderState().setBlendMode(RenderState.BlendMode.ALPHA_BLEND);
        
        // 创建简单的测试纹理
        BufferedImage testImage = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                testImage.setRGB(x, y, 0xFFFF0000); // 红色
            }
        }
        
        io.github.tfgcn.fieldguide.render3d.renderer.Image image = 
            new io.github.tfgcn.fieldguide.render3d.renderer.Image(testImage);
        Texture texture = new Texture(image);
        texture.setName("test_static_texture");
        
        material.setDiffuseMap(texture);
        return material;
    }
    
    /**
     * 创建动画材质（使用第一帧）
     */
    private static Material createAnimatedMaterial() {
        // 创建动画纹理图集（16x32，2帧）
        BufferedImage animationAtlas = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB);
        
        // 第一帧：红色
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                animationAtlas.setRGB(x, y, 0xFFFF0000);
            }
        }
        
        // 第二帧：绿色
        for (int x = 0; x < 16; x++) {
            for (int y = 16; y < 32; y++) {
                animationAtlas.setRGB(x, y, 0xFF00FF00);
            }
        }
        
        // 创建动画纹理
        AnimatedTexture animatedTexture = new AnimatedTexture();
        animatedTexture.setTexturePath("test_animation");
        animatedTexture.setAnimated(true);
        animatedTexture.setFrameCount(2);
        animatedTexture.setFrames(java.util.List.of(
            animationAtlas.getSubimage(0, 0, 16, 16),  // 第一帧
            animationAtlas.getSubimage(0, 16, 16, 16)  // 第二帧
        ));
        animatedTexture.setFrameRate(10.0f);
        
        // 创建动画材质
        AnimatedMaterial material = new AnimatedMaterial(animatedTexture);
        material.getRenderState().setAlphaTest(true);
        material.getRenderState().setAlphaFalloff(0.1f);
        material.getRenderState().setBlendMode(RenderState.BlendMode.ALPHA_BLEND);
        
        return material;
    }
}