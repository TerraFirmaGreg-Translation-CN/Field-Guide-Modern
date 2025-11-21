package io.github.tfgcn.fieldguide.render;

import io.github.tfgcn.fieldguide.asset.AssetKey;
import io.github.tfgcn.fieldguide.asset.AssetLoader;
import io.github.tfgcn.fieldguide.exception.AssetNotFoundException;
import io.github.tfgcn.fieldguide.data.minecraft.blockmodel.BlockModel;
import io.github.tfgcn.fieldguide.data.minecraft.blockmodel.ElementFace;
import io.github.tfgcn.fieldguide.data.minecraft.blockmodel.ElementRotation;
import io.github.tfgcn.fieldguide.data.minecraft.blockmodel.ModelElement;
import io.github.tfgcn.fieldguide.render3d.material.Material;
import io.github.tfgcn.fieldguide.render3d.material.RenderState;
import io.github.tfgcn.fieldguide.render3d.material.Texture;
import io.github.tfgcn.fieldguide.render3d.math.*;
import io.github.tfgcn.fieldguide.render3d.renderer.Image;
import io.github.tfgcn.fieldguide.render3d.scene.Geometry;
import io.github.tfgcn.fieldguide.render3d.scene.Mesh;
import io.github.tfgcn.fieldguide.render3d.scene.Node;
import io.github.tfgcn.fieldguide.render3d.shader.UnshadedShader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class BaseModelBuilder {
    
    public static final int SCALER = 16;
    public static final float SCALE = 1f / SCALER;

    protected static final Vector4f LIGHT = new Vector4f(1f, 1f, 1f, 1f);      // 顶部
    protected static final Vector4f LIGHT_GRAY = new Vector4f(0.8f, 0.8f, 0.8f, 1f);  // 北面和南面
    protected static final Vector4f DARK_GRAY = new Vector4f(0.6f, 0.6f, 0.6f, 1f);   // 东面和西面
    protected static final Vector4f DARK = new Vector4f(0.5f, 0.5f, 0.5f, 1f);        // 底部

    protected static final Vector4f[] COLOR_LIGHT = {LIGHT, LIGHT, LIGHT, LIGHT};
    protected static final Vector4f[] COLOR_LIGHT_GRAY = {LIGHT_GRAY, LIGHT_GRAY, LIGHT_GRAY, LIGHT_GRAY};
    protected static final Vector4f[] COLOR_DARK_GRAY = {DARK_GRAY, DARK_GRAY, DARK_GRAY, DARK_GRAY};
    protected static final Vector4f[] COLOR_DARK = {DARK, DARK, DARK, DARK};

    protected static final Vector3f UP = new Vector3f(0, 1, 0);
    protected static final Vector3f DOWN = new Vector3f(0, -1, 0);
    protected static final Vector3f EAST = new Vector3f(1, 0, 0);
    protected static final Vector3f WEST = new Vector3f(-1, 0, 0);
    protected static final Vector3f NORTH = new Vector3f(0, 0, -1);
    protected static final Vector3f SOUTH = new Vector3f(0, 0, 1);

    protected AssetLoader assetLoader;
    protected Map<String, Material> materialCache = new HashMap<>();

    public BaseModelBuilder(AssetLoader assetLoader) {
        this.assetLoader = assetLoader;
    }

    public Node buildModel(String modelId) {
        BlockModel blockModel = loadModel(modelId);
        return buildModel(blockModel);
    }

    /**
     * 构建模型节点
     */
    public Node buildModel(BlockModel blockModel) {
        Map<String, String> textures = blockModel.getTextures();
        Node node = new Node();
        for (ModelElement element : blockModel.getElements()) {
            buildNode(node, element, textures);
        }
        return node;
    }

    /**
     * 加载模型 - 子类可以覆盖此方法以提供不同的加载逻辑
     */
    protected BlockModel loadModel(String modelId) {
        return assetLoader.loadModel(modelId);
    }

    /**
     * 构建节点元素
     */
    public void buildNode(Node rootNode, ModelElement element, Map<String, String> textures) {
        Map<String, ElementFace> faces = element.getFaces();
        if (faces == null || faces.isEmpty()) {
            return;
        }

        double x1 = element.getFrom()[0];
        double y1 = element.getFrom()[1];
        double z1 = element.getFrom()[2];
        double x2 = element.getTo()[0];
        double y2 = element.getTo()[1];
        double z2 = element.getTo()[2];

        // 旋转原点
        double o1 = (x1 + x2) * 0.5;
        double o2 = (y1 + y2) * 0.5;
        double o3 = (z1 + z2) * 0.5;

        // 处理旋转
        ElementRotation rotation = element.getRotation();
        Quaternion rot = processRotation(rotation, element);
        if (rot != null && rotation.getOrigin() != null) {
            double[] origin = rotation.getOrigin();
            o1 = origin[0];
            o2 = origin[1];
            o3 = origin[2];
        }

        // 不应用阴影
        boolean noShade = element.getShade() != null && !element.getShade();

        // 相对于原点调整坐标
        x1 -= o1;
        y1 -= o2;
        z1 -= o3;
        x2 -= o1;
        y2 -= o2;
        z2 -= o3;

        // 处理每个面
        for (Map.Entry<String, ElementFace> entry : faces.entrySet()) {
            String dir = entry.getKey();
            ElementFace face = entry.getValue();

            Geometry geometry = createFaceGeometry(dir, face, x1, y1, z1, x2, y2, z2, noShade);
            if (geometry != null) {
                String texture = getTexture(textures, face.getTexture());
                Material material = makeMaterial(texture);
                geometry.setMaterial(material);

                // 应用旋转和位移
                if (rot != null) {
                    geometry.getLocalTransform().setRotation(rot);
                }
                geometry.getLocalTransform().setTranslation(v3(o1, o2, o3));

                rootNode.attachChild(geometry);
            }
        }
    }

    /**
     * 处理旋转
     */
    protected Quaternion processRotation(ElementRotation rotation, ModelElement element) {
        if (rotation == null || rotation.getAxis() == null || rotation.getAngle() == null || rotation.getAngle() == 0) {
            return null;
        }

        String axis = rotation.getAxis();
        double angle = rotation.getAngle(); // 角度制
        double rad = Math.toRadians(angle);

        Quaternion rot;
        switch (axis) {
            case "x":
                rot = new Quaternion().rotateX((float) rad);
                break;
            case "y":
                rot = new Quaternion().rotateY((float) rad);
                break;
            case "z":
                rot = new Quaternion().rotateZ((float) rad);
                break;
            default:
                log.error("Unknown axis: {}", axis);
                return null;
        }

        // 处理缩放（TODO）
        if (rotation.getRescale() != null && rotation.getRescale()) {
            processRescale(angle);
        }

        return rot;
    }

    /**
     * 处理缩放 - 待实现
     */
    protected void processRescale(double angle) {
        // TODO: 实现缩放逻辑
        if (angle == 45.0 || angle == -45.0) {
            // double rescale = 1.41421356237309
        } else if (angle == 22.5 || angle == -22.5) {
            // double rescale = 0.76536686473018
        }
    }

    /**
     * 创建面的几何体
     */
    protected Geometry createFaceGeometry(String dir, ElementFace face, 
                                        double x1, double y1, double z1, 
                                        double x2, double y2, double z2, 
                                        boolean noShade) {
        // 纹理坐标
        Vector2f[] texCoords = createTextureCoords(face);

        // 索引
        int[] indices = createIndices(dir, face);

        // 顶点位置、法线和颜色
        FaceData faceData = createFaceData(dir, x1, y1, z1, x2, y2, z2, noShade);
        if (faceData == null) {
            return null;
        }

        // 处理色调索引
        if (face.getTintIndex() != null && face.getTintIndex() >= 0) {
            // TODO: 处理色调
            log.info("tintindex: {}", face.getTintIndex());
        }

        Mesh mesh = new Mesh(faceData.positions, indices, texCoords, faceData.normals, faceData.colors);
        return new Geometry(mesh);
    }

    /**
     * 创建纹理坐标
     */
    protected Vector2f[] createTextureCoords(ElementFace face) {
        double s1 = face.getUv()[0];
        double t1 = face.getUv()[1];
        double s2 = face.getUv()[2];
        double t2 = face.getUv()[3];

        Vector2f uv0 = v2(s1, t1);
        Vector2f uv1 = v2(s1, t2);
        Vector2f uv2 = v2(s2, t2);
        Vector2f uv3 = v2(s2, t1);

        Vector2f[] texCoords = new Vector2f[]{uv0, uv1, uv2, uv3};

        // 处理纹理旋转
        if (face.getRotation() != null && face.getRotation() > 0) {
            switch (face.getRotation()) {
                case 90:
                    texCoords = new Vector2f[]{uv1, uv2, uv3, uv0};
                    break;
                case 180:
                    texCoords = new Vector2f[]{uv2, uv1, uv0, uv3};
                    break;
                case 270:
                    texCoords = new Vector2f[]{uv3, uv0, uv1, uv2};
                    break;
            }
        }

        return texCoords;
    }

    /**
     * 创建索引
     */
    protected int[] createIndices(String dir, ElementFace face) {
        int[] indices = {0, 1, 2, 0, 2, 3};

        // 处理背面剔除
        if (face.getCullface() != null && !dir.equals(face.getCullface())) {
            indices = new int[]{0, 2, 1, 0, 3, 2};
            // TODO: 改变法线方向
        }

        return indices;
    }

    /**
     * 创建面的数据
     */
    protected FaceData createFaceData(String dir, double x1, double y1, double z1, 
                                    double x2, double y2, double z2, boolean noShade) {
        switch (dir) {
            case "down":
                return createDownFace(x1, y1, z1, x2, y2, z2, noShade);
            case "up":
                return createUpFace(x1, y1, z1, x2, y2, z2, noShade);
            case "north":
                return createNorthFace(x1, y1, z1, x2, y2, z2, noShade);
            case "south":
                return createSouthFace(x1, y1, z1, x2, y2, z2, noShade);
            case "west":
                return createWestFace(x1, y1, z1, x2, y2, z2, noShade);
            case "east":
                return createEastFace(x1, y1, z1, x2, y2, z2, noShade);
            default:
                return null;
        }
    }

    // 各个面的创建方法
    protected FaceData createDownFace(double x1, double y1, double z1, double x2, double y2, double z2, boolean noShade) {
        Vector3f[] positions = {
            v3(x1, y1, z2), v3(x1, y1, z1), v3(x2, y1, z1), v3(x2, y1, z2)
        };
        Vector4f[] colors = noShade ? COLOR_LIGHT : COLOR_DARK;
        
        return new FaceData(positions, new Vector3f[]{DOWN, DOWN, DOWN, DOWN}, colors);
    }

    protected FaceData createUpFace(double x1, double y1, double z1, double x2, double y2, double z2, boolean noShade) {
        Vector3f[] positions = {
            v3(x1, y2, z1), v3(x1, y2, z2), v3(x2, y2, z2), v3(x2, y2, z1)
        };
        Vector4f[] colors = COLOR_LIGHT;
        
        return new FaceData(positions, new Vector3f[]{UP, UP, UP, UP}, colors);
    }

    protected FaceData createNorthFace(double x1, double y1, double z1, double x2, double y2, double z2, boolean noShade) {
        Vector3f[] positions = {
            v3(x2, y2, z1), v3(x2, y1, z1), v3(x1, y1, z1), v3(x1, y2, z1)
        };
        Vector4f[] colors = noShade ? COLOR_LIGHT : COLOR_LIGHT_GRAY;
        
        return new FaceData(positions, new Vector3f[]{NORTH, NORTH, NORTH, NORTH}, colors);
    }

    protected FaceData createSouthFace(double x1, double y1, double z1, double x2, double y2, double z2, boolean noShade) {
        Vector3f[] positions = {
            v3(x1, y2, z2), v3(x1, y1, z2), v3(x2, y1, z2), v3(x2, y2, z2)
        };
        Vector4f[] colors = noShade ? COLOR_LIGHT : COLOR_LIGHT_GRAY;
        
        return new FaceData(positions, new Vector3f[]{SOUTH, SOUTH, SOUTH, SOUTH}, colors);
    }

    protected FaceData createWestFace(double x1, double y1, double z1, double x2, double y2, double z2, boolean noShade) {
        Vector3f[] positions = {
            v3(x1, y2, z1), v3(x1, y1, z1), v3(x1, y1, z2), v3(x1, y2, z2)
        };
        Vector4f[] colors = noShade ? COLOR_LIGHT : COLOR_DARK_GRAY;
        
        return new FaceData(positions, new Vector3f[]{WEST, WEST, WEST, WEST}, colors);
    }

    protected FaceData createEastFace(double x1, double y1, double z1, double x2, double y2, double z2, boolean noShade) {
        Vector3f[] positions = {
            v3(x2, y2, z2), v3(x2, y1, z2), v3(x2, y1, z1), v3(x2, y2, z1)
        };
        Vector4f[] colors = noShade ? COLOR_LIGHT : COLOR_DARK_GRAY;
        
        return new FaceData(positions, new Vector3f[]{EAST, EAST, EAST, EAST}, colors);
    }

    /**
     * 创建材质
     */
    protected Material makeMaterial(String texture) {
        return materialCache.computeIfAbsent(texture, this::createMaterial);
    }

    protected Material createMaterial(String texture) {
        AssetKey assetKey = new AssetKey(texture, "textures", "assets", ".png");
        BufferedImage img = assetLoader.loadTexture(assetKey);

        // write to file
        File imageFile = assetLoader.getOutputDir().resolve(assetKey.getResourcePath()).toFile();
        if (!imageFile.exists()) {
            try {
                FileUtils.createParentDirectories(imageFile);
                ImageIO.write(img, "png", imageFile);
            } catch (IOException e) {
                log.error("Error writing image: {}", imageFile, e);
            }
        }

        Image image = new Image(img);
        Texture diffuseMap = new Texture(image);
        diffuseMap.setName(assetKey.getResourcePath());
        diffuseMap.setMagFilter(Texture.MagFilter.NEAREST);

        Material material = new Material();
        material.getRenderState().setAlphaTest(true);
        material.getRenderState().setAlphaFalloff(0.1f);
        material.getRenderState().setBlendMode(RenderState.BlendMode.ALPHA_BLEND); // 设置混合模式
        material.setUseVertexColor(true);
        material.setShader(new UnshadedShader());
        material.setDiffuseMap(diffuseMap);

        return material;
    }

    /**
     * 解析纹理引用
     */
    protected String getTexture(Map<String, String> map, String id) {
        if (id.startsWith("#")) {
            String ref = map.get(id.substring(1));
            if (ref == null) {
                throw new AssetNotFoundException("Texture not found: " + id);
            }
            if (ref.startsWith("#")) {
                return getTexture(map, ref);
            } else {
                return ref;
            }
        } else {
            return map.getOrDefault(id, "assets/minecraft/textures/block/missing.png");
        }
    }

    // 工具方法
    public static Vector3f v3(double x, double y, double z) {
        return new Vector3f((float)(x * SCALE), (float)(y * SCALE), (float)(z * SCALE));
    }

    public static Vector2f v2(double s, double t) {
        return new Vector2f((float) (s / 16.0), (float) (1.0 - t / 16.0));
    }

    /**
     * 面数据容器类
     */
    protected static class FaceData {
        public final Vector3f[] positions;
        public final Vector3f[] normals;
        public final Vector4f[] colors;

        public FaceData(Vector3f[] positions, Vector3f[] normals, Vector4f[] colors) {
            this.positions = positions;
            this.normals = normals;
            this.colors = colors;
        }
    }
}