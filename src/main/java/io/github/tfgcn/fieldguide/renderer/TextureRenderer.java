package io.github.tfgcn.fieldguide.renderer;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.RescaleOp;

public class TextureRenderer {
    
    // 透视变换的目标坐标（优化后的坐标）
    private static final Point[] LEFT_FACE_POINTS = {
        new Point(16, 231),   // 左下
        new Point(150, 298),  // 右下
        new Point(150, 135),  // 右上
        new Point(16, 68)     // 左上
    };

    private static final Point[] RIGHT_FACE_POINTS = {
        new Point(150, 298),  // 左下
        new Point(283, 231),  // 右下
        new Point(283, 68),   // 右上
        new Point(150, 135)   // 左上
    };

    private static final Point[] TOP_FACE_POINTS = {
        new Point(16, 68),    // 左下
        new Point(150, 136),  // 右下（连接点）
        new Point(284, 68),   // 右上
        new Point(150, 0)    // 左上
    };

    public static BufferedImage createBlockImage(BufferedImage leftTexture, 
                                               BufferedImage rightTexture, 
                                               BufferedImage topTexture) {
        BufferedImage result = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = result.createGraphics();
        
        // 设置高质量渲染
        setupHighQualityRendering(g2d);
        
        // 透明背景
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, 300, 300);
        g2d.setComposite(AlphaComposite.SrcOver);
        
        // 使用更高效的亮度调整
        BufferedImage darkLeft = adjustBrightness(leftTexture, 0.85f);
        BufferedImage darkRight = adjustBrightness(rightTexture, 0.6f);
        
        // 使用改进的纹理映射
        drawTexturedPolygon(g2d, darkLeft, LEFT_FACE_POINTS);
        drawTexturedPolygon(g2d, darkRight, RIGHT_FACE_POINTS);
        drawTexturedPolygon(g2d, topTexture, TOP_FACE_POINTS);
        
        g2d.dispose();
        return result;
    }
    
    private static void setupHighQualityRendering(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    }
    
    /**
     * 改进的纹理映射方法，使用透视变换
     */
    private static void drawTexturedPolygon(Graphics2D g2d, BufferedImage texture, Point[] points) {
        // 创建多边形路径
        Path2D polygon = new Path2D.Double();
        polygon.moveTo(points[0].x, points[0].y);
        for (int i = 1; i < points.length; i++) {
            polygon.lineTo(points[i].x, points[i].y);
        }
        polygon.closePath();
        
        // 保存原始裁剪区域
        Shape oldClip = g2d.getClip();
        g2d.setClip(polygon);
        
        try {
            // 计算多边形的边界
            Rectangle bounds = polygon.getBounds();
            
            // 对于平行四边形面，使用仿射变换
            if (isParallelogram(points)) {
                drawParallelogramTexture(g2d, texture, points, bounds);
            } else {
                // 对于梯形面，使用更复杂的变换
                drawTrapezoidTexture(g2d, texture, points, bounds);
            }
        } finally {
            // 恢复裁剪区域
            g2d.setClip(oldClip);
        }
    }
    
    /**
     * 检查是否为平行四边形
     */
    private static boolean isParallelogram(Point[] points) {
        if (points.length != 4) return false;
        
        // 检查对边是否平行
        double dx1 = points[1].x - points[0].x;
        double dy1 = points[1].y - points[0].y;
        double dx2 = points[2].x - points[3].x;
        double dy2 = points[2].y - points[3].y;
        
        // 简单的平行检查（向量叉积接近0）
        double cross1 = dx1 * dy2 - dy1 * dx2;
        return Math.abs(cross1) < 1.0;
    }
    
    /**
     * 绘制平行四边形纹理
     */
    private static void drawParallelogramTexture(Graphics2D g2d, BufferedImage texture, 
                                               Point[] points, Rectangle bounds) {
        // 计算仿射变换矩阵
        double sx1 = points[1].x - points[0].x;
        double sy1 = points[1].y - points[0].y;
        double sx2 = points[3].x - points[0].x;
        double sy2 = points[3].y - points[0].y;
        
        AffineTransform transform = new AffineTransform(
            sx1 / texture.getWidth(), sy1 / texture.getHeight(),
            sx2 / texture.getWidth(), sy2 / texture.getHeight(),
            points[0].x, points[0].y
        );
        
        g2d.drawImage(texture, transform, null);
    }
    
    /**
     * 绘制梯形纹理（简化版本）
     */
    private static void drawTrapezoidTexture(Graphics2D g2d, BufferedImage texture, 
                                           Point[] points, Rectangle bounds) {
        // 对于梯形，使用缩放和平移（这是一个简化方案）
        // 在实际应用中，您可能需要真正的透视变换
        
        double scaleX = (double) bounds.width / texture.getWidth();
        double scaleY = (double) bounds.height / texture.getHeight();
        
        AffineTransform transform = new AffineTransform();
        transform.translate(bounds.x, bounds.y);
        transform.scale(scaleX, scaleY);
        
        g2d.drawImage(texture, transform, null);
    }

    public static BufferedImage adjustBrightness(BufferedImage image, float factor) {
        // 检查图像类型
        boolean isIndexed = isIndexedImage(image);

        if (!isIndexed) {
            // 对于非索引图像，使用快速的RescaleOp
            return adjustBrightnessFast(image, factor);
        } else {
            // 对于索引图像，使用精确的逐像素方法
            return adjustBrightnessPrecise(image, factor);
        }
    }

    public static boolean isIndexedImage(BufferedImage image) {
        int type = image.getType();
        return type == BufferedImage.TYPE_BYTE_INDEXED ||
                type == BufferedImage.TYPE_BYTE_BINARY ||
                image.getColorModel() instanceof IndexColorModel;
    }

    public static BufferedImage adjustBrightnessFast(BufferedImage image, float factor) {
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), 
                                               BufferedImage.TYPE_INT_ARGB);
        
        // 使用RescaleOp进行快速调整
        float[] scales = { factor, factor, factor, 1.0f }; // RGB缩放，Alpha不变
        float[] offsets = new float[4];
        RescaleOp op = new RescaleOp(scales, offsets, null);
        
        return op.filter(image, result);
    }

    public static BufferedImage adjustBrightnessPrecise(BufferedImage image, float factor) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);
        
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            int a = (argb >> 24) & 0xFF;
            int r = (int) (((argb >> 16) & 0xFF) * factor);
            int g = (int) (((argb >> 8) & 0xFF) * factor);
            int b = (int) ((argb & 0xFF) * factor);
            
            r = Math.min(255, Math.max(0, r));
            g = Math.min(255, Math.max(0, g));
            b = Math.min(255, Math.max(0, b));
            
            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        
        result.setRGB(0, 0, width, height, pixels, 0, width);
        return result;
    }

}