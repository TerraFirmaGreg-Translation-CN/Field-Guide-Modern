package io.github.tfgcn.fieldguide.render3d.animation;

import io.github.tfgcn.fieldguide.render3d.material.Material;
import io.github.tfgcn.fieldguide.render3d.material.Texture;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * 支持动画的材质
 */
@Slf4j
public class AnimatedMaterial extends Material {
    
    private final AnimatedTexture animatedTexture;
    private int currentFrame = 0;
    private long lastFrameTime = 0;
    private boolean animationEnabled = true;
    
    public AnimatedMaterial(AnimatedTexture animatedTexture) {
        this.animatedTexture = animatedTexture;
        initializeFrames();
    }
    
    /**
     * 初始化动画帧
     */
    private void initializeFrames() {
        if (animatedTexture.isAnimated() && !animatedTexture.getFrames().isEmpty()) {
            // 创建初始纹理
            updateFrameTexture(animatedTexture.getFrames().get(0));
            log.debug("Initialized animated material with {} frames", animatedTexture.getFrameCount());
        }
    }
    
    /**
     * 更新当前帧
     */
    public void updateAnimation(long currentTime) {
        if (!animationEnabled || !animatedTexture.isAnimated()) {
            return;
        }
        
        long frameDuration = (long)(1000.0f / animatedTexture.getFrameRate());
        
        if (currentTime - lastFrameTime >= frameDuration) {
            currentFrame++;
            lastFrameTime = currentTime;
            
            if (currentFrame >= animatedTexture.getFrames().size()) {
                if (animatedTexture.isLoop()) {
                    currentFrame = 0;
                } else {
                    currentFrame = animatedTexture.getFrames().size() - 1;
                    animationEnabled = false;
                }
            }
            
            updateFrameTexture(animatedTexture.getFrames().get(currentFrame));
        }
    }
    
    /**
     * 更新帧纹理
     */
    private void updateFrameTexture(BufferedImage frameImage) {
        // 更新材质的纹理
        if (getDiffuseMap() != null) {
            // 创建新的图像对象
            io.github.tfgcn.fieldguide.render3d.renderer.Image newImage = 
                new io.github.tfgcn.fieldguide.render3d.renderer.Image(frameImage);
            getDiffuseMap().setImage(newImage);
        }
    }
    
    /**
     * 获取当前帧索引
     */
    public int getCurrentFrame() {
        return currentFrame;
    }
    
    /**
     * 设置当前帧
     */
    public void setCurrentFrame(int frame) {
        if (frame >= 0 && frame < animatedTexture.getFrames().size()) {
            currentFrame = frame;
            updateFrameTexture(animatedTexture.getFrames().get(currentFrame));
        }
    }
    
    /**
     * 重置动画
     */
    public void resetAnimation() {
        currentFrame = 0;
        lastFrameTime = 0;
        animationEnabled = true;
        updateFrameTexture(animatedTexture.getFrames().get(0));
    }
    
    /**
     * 启用/禁用动画
     */
    public void setAnimationEnabled(boolean enabled) {
        this.animationEnabled = enabled;
    }
    
    /**
     * 获取动画信息
     */
    public AnimatedTexture getAnimatedTexture() {
        return animatedTexture;
    }
    
    /**
     * 是否为动画材质
     */
    public boolean isAnimated() {
        return animatedTexture.isAnimated();
    }
}