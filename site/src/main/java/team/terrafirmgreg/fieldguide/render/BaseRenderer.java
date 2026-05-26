package team.terrafirmgreg.fieldguide.render;

import team.terrafirmgreg.fieldguide.export.ExportModelLoader;
import team.terrafirmgreg.fieldguide.render3d.math.ColorRGBA;
import team.terrafirmgreg.fieldguide.render3d.renderer.Camera;
import team.terrafirmgreg.fieldguide.render3d.renderer.Image;
import team.terrafirmgreg.fieldguide.render3d.renderer.Renderer;
import team.terrafirmgreg.fieldguide.render3d.scene.Geometry;
import team.terrafirmgreg.fieldguide.render3d.scene.Node;
import lombok.Getter;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.List;

@Getter
public abstract class BaseRenderer {
    
    protected Renderer renderer;
    protected Camera camera;
    protected Node rootNode;
    protected int width;
    protected int height;
    protected ExportModelLoader assetLoader;
    protected BaseModelBuilder modelBuilder;

    public BaseRenderer(BaseModelBuilder modelBuilder, int width, int height) {
        this.width = width;
        this.height = height;
        this.modelBuilder = modelBuilder;
        initializeRenderer();
    }

    /**
     * 初始化渲染器
     */
    protected void initializeRenderer() {
        // 创建渲染器
        renderer = new Renderer(width, height);
        renderer.setBackgroundColor(ColorRGBA.BLACK_NO_ALPHA);
        renderer.setLights(List.of());

        // 创建摄像机
        camera = new Camera(width, height);

        // 场景根节点
        rootNode = new Node();
    }

    /**
     * 渲染场景
     */
    protected BufferedImage render() {
        // 清空场景
        renderer.clear();

        // 获取所有物体，绘制3D场景
        List<Geometry> geomList = rootNode.getGeometryList(null);
        renderer.render(geomList, camera);

        // 获取纹理
        return toImage(renderer.getRenderContext());
    }

    /**
     * 转换图像格式
     */
    protected BufferedImage toImage(Image image) {
        // 用于显示的图像
        BufferedImage displayImage = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        byte[] displayComponents = ((DataBufferByte) displayImage.getRaster().getDataBuffer()).getData();

        // 把渲染好的图像拷贝到BufferedImage中
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] components = image.getComponents();
        int length = width * height;
        for (int i = 0; i < length; i++) {
            displayComponents[i * 4 + 3] = components[i * 4];     // red
            displayComponents[i * 4 + 2] = components[i * 4 + 1]; // green
            displayComponents[i * 4 + 1] = components[i * 4 + 2]; // blue
            displayComponents[i * 4] = components[i * 4 + 3];     // alpha
        }
        return displayImage;
    }
}