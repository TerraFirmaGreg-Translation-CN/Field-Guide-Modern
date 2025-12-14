package su.terrafirmgreg.fieldguide.render3d.animation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * 动画纹理信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnimatedTexture {
    
    /**
     * 纹理路径
     */
    private String texturePath;
    
    /**
     * 是否为动画纹理
     */
    private boolean animated;
    
    /**
     * 帧数
     */
    private int frameCount;
    
    /**
     * 每帧的图像
     */
    private List<BufferedImage> frames;
    
    /**
     * 动画时长（毫秒）
     */
    private int duration = 1000;
    
    /**
     * 帧率
     */
    private float frameRate = 10.0f;
    
    /**
     * 是否循环播放
     */
    private boolean loop = true;
    
    /**
     * 动画时间访问器索引（用于glTF导出）
     */
    private int animationTimeAccessor = -1;
    
    /**
     * 纹理索引访问器索引（用于glTF导出）
     */
    private int textureIndexAccessor = -1;
    
    /**
     * 检测是否为动画纹理图集
     */
    public static boolean isAnimationAtlas(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        return width == 16 && height > 16 && height % 16 == 0;
    }
    
    /**
     * 计算帧数
     */
    public static int calculateFrameCount(BufferedImage img) {
        if (!isAnimationAtlas(img)) {
            return 1;
        }
        return img.getHeight() / 16;
    }
    
    /**
     * 提取所有帧
     */
    public static List<BufferedImage> extractFrames(BufferedImage img) {
        if (!isAnimationAtlas(img)) {
            return List.of(img);
        }
        
        int frameCount = img.getHeight() / 16;
        return java.util.stream.IntStream.range(0, frameCount)
                .mapToObj(i -> img.getSubimage(0, i * 16, 16, 16))
                .collect(java.util.stream.Collectors.toList());
    }
    
    // Getter和Setter方法
    public int getAnimationTimeAccessor() {
        return animationTimeAccessor;
    }
    
    public void setAnimationTimeAccessor(int animationTimeAccessor) {
        this.animationTimeAccessor = animationTimeAccessor;
    }
    
    public int getTextureIndexAccessor() {
        return textureIndexAccessor;
    }
    
    public void setTextureIndexAccessor(int textureIndexAccessor) {
        this.textureIndexAccessor = textureIndexAccessor;
    }
}