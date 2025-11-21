package io.github.tfgcn.fieldguide.render;

import io.github.tfgcn.fieldguide.asset.AssetLoader;
import io.github.tfgcn.fieldguide.exception.AssetNotFoundException;
import io.github.tfgcn.fieldguide.data.minecraft.blockmodel.BlockModel;
import io.github.tfgcn.fieldguide.data.minecraft.blockmodel.ElementFace;
import io.github.tfgcn.fieldguide.data.minecraft.blockmodel.ElementRotation;
import io.github.tfgcn.fieldguide.data.minecraft.blockmodel.ModelElement;
import io.github.tfgcn.fieldguide.render3d.material.Material;
import io.github.tfgcn.fieldguide.render3d.material.Texture;
import io.github.tfgcn.fieldguide.render3d.math.*;
import io.github.tfgcn.fieldguide.render3d.renderer.Camera;
import io.github.tfgcn.fieldguide.render3d.renderer.Image;
import io.github.tfgcn.fieldguide.render3d.renderer.Renderer;
import io.github.tfgcn.fieldguide.render3d.scene.Geometry;
import io.github.tfgcn.fieldguide.render3d.scene.Mesh;
import io.github.tfgcn.fieldguide.render3d.scene.Node;
import io.github.tfgcn.fieldguide.render3d.shader.UnshadedShader;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * desc:
 *
 * @author yanmaoyuan
 */
@Slf4j
public class Block3DRenderer {

    static int SCALER = 16;
    static float SCALE = 1f / SCALER;

    Vector4f LIGHT = new Vector4f(1f, 1f, 1f, 1f);// top
    Vector4f LIGHT_GRAY = new Vector4f(0.8f, 0.8f, 0.8f, 1f);// north and south
    Vector4f DARK_GRAY = new Vector4f(0.6f, 0.6f, 0.6f, 1f);// east and west
    Vector4f DARK = new Vector4f(0.5f, 0.5f, 0.5f, 1f);// down

    Vector3f UP = new Vector3f(0, 1, 0);
    Vector3f DOWN = new Vector3f(0, -1, 0);
    Vector3f EAST = new Vector3f(1, 0, 0);
    Vector3f WEST = new Vector3f(-1, 0, 0);
    Vector3f NORTH = new Vector3f(0, 0, -1);
    Vector3f SOUTH = new Vector3f(0, 0, 1);

    private Renderer renderer;
    private Camera camera;
    private Node rootNode;

    private int width;
    private int height;

    private AssetLoader assetLoader;

    public Block3DRenderer(AssetLoader assetLoader, int width, int height) {
        this.assetLoader = assetLoader;
        this.width = width;
        this.height = height;

        // 创建渲染器
        renderer = new Renderer(width, height);
        renderer.setBackgroundColor(ColorRGBA.BLACK_NO_ALPHA);
        renderer.setLights(List.of());

        // 创建摄像机
        camera = new Camera(width, height);

        // parallel
        camera.setParallel(-11 * SCALE, 11 * SCALE, -11 * SCALE, 11 * SCALE, -1000f, 1000f);
        camera.lookAt(v3(32f, 32f, 32f), v3(8, 8, 8), Vector3f.UNIT_Y);
        //camera.lookAt(v3(32f, 32f, -16f), v3(8, 8, 8), Vector3f.UNIT_Y);

        rootNode = new Node();
    }

    /**
     * 渲染模型
     * @param modelId
     * @return
     */
    public BufferedImage render(String modelId) {
        Node node = buildModel(modelId);
        rootNode.attachChild(node);
        BufferedImage image = render();
        rootNode.detachChild(node);
        return image;
    }

    public BufferedImage render(BlockModel model) {
        Node node = buildModel(model);
        rootNode.attachChild(node);
        BufferedImage image = render();
        rootNode.detachChild(node);
        return image;
    }

    /**
     * 绘制画面
     */
    private BufferedImage render() {
        // 清空场景
        renderer.clear();

        // 获取所有物体，绘制3D场景
        List<Geometry> geomList = rootNode.getGeometryList(null);
        renderer.render(geomList, camera);

        // 获取纹理
        return toImage(renderer.getRenderContext());
    }

    /**
     * 交换缓冲区，将渲染结果刷新到画布上。
     * @param image
     */
    private BufferedImage toImage(Image image) {
        // 用于显示的图像
        BufferedImage displayImage = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        byte[] displayComponents = ((DataBufferByte) displayImage.getRaster().getDataBuffer()).getData();

        // 把渲染好的图像拷贝到BufferedImage中。
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] components = image.getComponents();
        int length = width * height;
        for (int i = 0; i < length; i++) {
            displayComponents[i * 4 + 3] = components[i * 4];// red
            displayComponents[i * 4 + 2] = components[i * 4 + 1];// green
            displayComponents[i * 4 + 1] = components[i * 4 + 2];// blue
            displayComponents[i * 4] = components[i * 4 + 3];// alpha
        }
        return displayImage;
    }

    private Vector3f v3(double x, double y, double z) {
        return new Vector3f((float)(x * SCALE), (float)(y * SCALE), (float)(z * SCALE));
    }

    private Vector2f v2(double s, double t) {
        return new Vector2f((float) (s / 16.0), (float) (1.0 - t / 16.0));
    }

    public Node buildModel(String modelId) {
        BlockModel blockModel = assetLoader.loadModel(modelId);
        return buildModel(blockModel);
    }

    public Node buildModel(BlockModel blockModel) {
        Map<String, String> textures = blockModel.getTextures();
        Node node = new Node();
        for (ModelElement element : blockModel.getElements()) {
            buildNode(node, element, textures);
        }

        return node;
    }


    Map<String, Material> materialCache = new HashMap<>();

    private Material makeMaterial(String texture) {
        if (materialCache.containsKey(texture)) {
            return materialCache.get(texture);
        }

        BufferedImage img = assetLoader.loadTexture(texture);
        Image image = new Image(img);
        Texture diffuseMap = new Texture(image);
        diffuseMap.setMagFilter(Texture.MagFilter.NEAREST);

        Material material = new Material();
        material.getRenderState().setAlphaTest(true);
        material.getRenderState().setAlphaFalloff(0.1f);
        material.setUseVertexColor(true);
        material.setShader(new UnshadedShader());
        material.setDiffuseMap(diffuseMap);

        materialCache.put(texture, material);
        return material;
    }

    private String getTexture(Map<String, String> map, String id) {
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

        // origin, for rotation
        double o1 = (x1 + x2) * 0.5;
        double o2 = (y1 + y2) * 0.5;
        double o3 = (z1 + z2) * 0.5;

        ElementRotation rotation = element.getRotation();
        Quaternion rot = null;
        if (rotation != null) {
            double[] origin = rotation.getOrigin();
            if (origin != null && origin.length == 3) {
                o1 = origin[0];
                o2 = origin[1];
                o3 = origin[2];
            } else {
                System.out.println("origin is null!!!");
            }

            if (rotation.getAxis() != null && rotation.getAngle() != null && rotation.getAngle() != 0) {
                String axis = rotation.getAxis();
                double angle = rotation.getAngle();// in degrees
                double rad = 0.017453292 * angle;

                switch(axis) {
                    case "x": {
                        rot = new Quaternion().rotateX((float) rad);
                        break;
                    }
                    case "y": {
                        rot = new Quaternion().rotateY((float) rad);
                        break;
                    }
                    case "z": {
                        rot = new Quaternion().rotateZ((float) rad);
                        break;
                    }
                    default: {
                        log.error("Unknown axis: {}", axis);
                        rot = null;
                        break;
                    }
                }

                // when abs(angle) == 22.5 or 45, apply the rescale
                if (rotation.getRescale() != null && rotation.getRescale()) {
                    if (angle == 45.0 || angle == -45.0) {
                        // TODO double rescale = 1.41421356237309
                    } else if (angle == 22.5 || angle == -22.5) {
                        // TODO double rescale = 0.76536686473018
                    }
                }
            }
        }

        // don't shade this block
        boolean noShade = element.getShade() != null && !element.getShade();

        x1 = x1 - o1;
        y1 = y1 - o2;
        z1 = z1 - o3;

        x2 = x2 - o1;
        y2 = y2 - o2;
        z2 = z2 - o3;

        for (Map.Entry<String, ElementFace> entry : faces.entrySet()) {
            String dir = entry.getKey();
            ElementFace face = entry.getValue();

            // TextureCoords
            double s1 = face.getUv()[0];
            double t1 = face.getUv()[1];
            double s2 = face.getUv()[2];
            double t2 = face.getUv()[3];

            Vector2f uv0 = v2(s1, t1);
            Vector2f uv1 = v2(s1, t2);
            Vector2f uv2 = v2(s2, t2);
            Vector2f uv3 = v2(s2, t1);

            Vector2f[] texCoords = new Vector2f[] {uv0, uv1, uv2, uv3};// rotation 0
            if (face.getRotation() != null && face.getRotation() > 0) {
                if (face.getRotation() == 90) {
                    texCoords = new Vector2f[] {uv1, uv2, uv3, uv0};
                } else if (face.getRotation() == 180) {
                    texCoords = new Vector2f[] {uv2, uv1, uv0, uv3};
                } else if (face.getRotation() == 270) {
                    texCoords = new Vector2f[] {uv3, uv0, uv1, uv2};
                }
            }

            // Index
            int[] index = {0, 1, 2, 0, 2, 3};

            if (face.getCullface() != null && !dir.equals(face.getCullface())) {
                // change face order to the nagative direction
                index = new int[] {0, 2, 1, 0, 3, 2};
                // TODO change normals
            }

            Vector3f[] positions;
            Vector3f[] normals;
            Vector4f[] colors;
            Mesh mesh;
            switch (dir) {
                case "down": {
                    Vector3f v0 = v3(x1, y1, z2);
                    Vector3f v1 = v3(x1, y1, z1);
                    Vector3f v2 = v3(x2, y1, z1);
                    Vector3f v3 = v3(x2, y1, z2);

                    positions = new Vector3f[]{v0, v1, v2, v3};
                    normals = new Vector3f[] {DOWN, DOWN, DOWN, DOWN};
                    colors = new Vector4f[] {DARK, DARK, DARK, DARK};
                    break;
                }

                case "up": {
                    Vector3f v0 = v3(x1, y2, z1);
                    Vector3f v1 = v3(x1, y2, z2);
                    Vector3f v2 = v3(x2, y2, z2);
                    Vector3f v3 = v3(x2, y2, z1);

                    positions = new Vector3f[]{v0, v1, v2, v3};
                    normals = new Vector3f[]{UP, UP, UP, UP};
                    colors = new Vector4f[]{LIGHT, LIGHT, LIGHT, LIGHT};
                    break;
                }
                case "north": {
                    Vector3f v0 = v3(x2, y2, z1);
                    Vector3f v1 = v3(x2, y1, z1);
                    Vector3f v2 = v3(x1, y1, z1);
                    Vector3f v3 = v3(x1, y2, z1);

                    positions = new Vector3f[]{v0, v1, v2, v3};
                    normals = new Vector3f[]{NORTH, NORTH, NORTH, NORTH};
                    colors = new Vector4f[]{LIGHT_GRAY, LIGHT_GRAY, LIGHT_GRAY, LIGHT_GRAY};
                    break;
                }
                case "south": {
                    Vector3f v0 = v3(x1, y2, z2);
                    Vector3f v1 = v3(x1, y1, z2);
                    Vector3f v2 = v3(x2, y1, z2);
                    Vector3f v3 = v3(x2, y2, z2);

                    positions = new Vector3f[]{v0, v1, v2, v3};
                    normals = new Vector3f[]{SOUTH, SOUTH, SOUTH, SOUTH};
                    colors = new Vector4f[]{LIGHT_GRAY, LIGHT_GRAY, LIGHT_GRAY, LIGHT_GRAY};
                    break;
                }
                case "west": {
                    Vector3f v0 = v3(x1, y2, z1);
                    Vector3f v1 = v3(x1, y1, z1);
                    Vector3f v2 = v3(x1, y1, z2);
                    Vector3f v3 = v3(x1, y2, z2);

                    positions = new Vector3f[]{v0, v1, v2, v3};
                    normals = new Vector3f[]{WEST, WEST, WEST, WEST};
                    colors = new Vector4f[]{DARK_GRAY, DARK_GRAY, DARK_GRAY, DARK_GRAY};
                    break;
                }
                case "east": {
                    Vector3f v0 = v3(x2, y2, z2);
                    Vector3f v1 = v3(x2, y1, z2);
                    Vector3f v2 = v3(x2, y1, z1);
                    Vector3f v3 = v3(x2, y2, z1);

                    positions = new Vector3f[]{v0, v1, v2, v3};
                    normals = new Vector3f[]{EAST, EAST, EAST, EAST};
                    colors = new Vector4f[]{DARK_GRAY, DARK_GRAY, DARK_GRAY, DARK_GRAY};
                    break;
                }
                default: {
                    return;
                }
            }

            if (noShade) {
                colors = new Vector4f[]{LIGHT, LIGHT, LIGHT, LIGHT};
            }

            if (face.getTintIndex() != null && face.getTintIndex() >= 0) {
                // TODO tintindex
                log.info("tintindex: {}", face.getTintIndex());
            }

            mesh = new Mesh(positions, index, texCoords, normals, colors);

            String texture = getTexture(textures, face.getTexture());
            Material material = makeMaterial(texture);
            Geometry geometry = new Geometry(mesh, material);
            rootNode.attachChild(geometry);

            // TODO，对于rotation 考虑直接改变顶点的坐标，而不是改变矩阵
            if (rot != null) {
                geometry.getLocalTransform().setRotation(rot);
            }
            geometry.getLocalTransform().setTranslation(v3(o1, o2, o3));
        }
    }
}
