package io.github.tfgcn.fieldguide.asset;

import io.github.jmecn.draw3d.Application;
import io.github.jmecn.draw3d.material.Material;
import io.github.jmecn.draw3d.material.Texture;
import io.github.jmecn.draw3d.math.Quaternion;
import io.github.jmecn.draw3d.math.Vector2f;
import io.github.jmecn.draw3d.math.Vector3f;
import io.github.jmecn.draw3d.math.Vector4f;
import io.github.jmecn.draw3d.renderer.Camera;
import io.github.jmecn.draw3d.renderer.Image;
import io.github.jmecn.draw3d.scene.Geometry;
import io.github.jmecn.draw3d.scene.Mesh;
import io.github.jmecn.draw3d.scene.Node;
import io.github.jmecn.draw3d.shader.UnshadedShader;
import io.github.tfgcn.fieldguide.mc.BlockModel;
import io.github.tfgcn.fieldguide.mc.ElementFace;
import io.github.tfgcn.fieldguide.mc.ModelElement;
import org.w3c.dom.Text;

import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;

/**
 * desc:
 *
 * @author yanmaoyuan
 */
public class RendererTest extends Application {

    public static void main(String[] args) {
        RendererTest app = new RendererTest();
        app.start();
    }
    //String model = "beneath:block/unposter";
    String model = "tfc:block/metal/anvil/bismuth_bronze";

    Vector4f LIGHT = new Vector4f(1);// top
    Vector4f LIGHT_GRAY = new Vector4f(0.8f);// north and south
    Vector4f DARK_GRAY = new Vector4f(0.6f);// east and west
    Vector4f DARK = new Vector4f(0.5f);// down

    Vector3f UP = new Vector3f(0, 1, 0);
    Vector3f DOWN = new Vector3f(0, -1, 0);
    Vector3f EAST = new Vector3f(1, 0, 0);
    Vector3f WEST = new Vector3f(-1, 0, 0);
    Vector3f NORTH = new Vector3f(0, 0, -1);
    Vector3f SOUTH = new Vector3f(0, 0, 1);

    private AssetLoader assetLoader;

    private Node node;

    public RendererTest() {
        this.width = 1080;
        this.height = 720;
        this.title = model;
    }

    @Override
    protected void initialize() {

        String modpackPath = "Modpack-Modern";
        assetLoader = new AssetLoader(Paths.get(modpackPath));

        BlockModel itemModel = assetLoader.loadItemModel("beneath:unposter");
        Node node = buildModel(itemModel.getParent());

        rootNode.attachChild(node);

        // 初始化摄像机
        Camera cam = getCamera();
        cam.lookAt(new Vector3f(30, 30, 30), new Vector3f(8, 8, 8), Vector3f.UNIT_Y);
    }

    @Override
    protected void update(float delta) {
    }

    double v3_scale = 1.0;
    Vector3f v3(double x, double y, double z) {
        return new Vector3f((float)(x * v3_scale), (float)(y * v3_scale), (float)(z * v3_scale));
    }
    Vector2f v2(double s, double t) {
        return new Vector2f((float)(s / 16.0), (float)(t / 16.0));
    }

    public Node buildModel(String modelId) {
        BlockModel blockModel = assetLoader.loadModel(modelId);
        Map<String, String> textures = blockModel.getTextures();
        Node node = new Node();
        for (ModelElement element : blockModel.getElements()) {
            buildNode(node, element, textures);
        }

        return node;
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

        for (Map.Entry<String, ElementFace> entry : faces.entrySet()) {
            String dir = entry.getKey();
            ElementFace face = entry.getValue();

            double s1 = face.getUv()[0];
            double t1 = face.getUv()[1];
            double s2 = face.getUv()[2];
            double t2 = face.getUv()[3];

            switch (dir) {
                case "down": {
                    Vector3f v0 = v3(x1, y1, z2);
                    Vector3f v1 = v3(x1, y1, z1);
                    Vector3f v2 = v3(x2, y1, z1);
                    Vector3f v3 = v3(x2, y1, z2);

                    Vector2f uv0 = v2(s1, 16 - t2);
                    Vector2f uv1 = v2(s2, 16 - t2);
                    Vector2f uv2 = v2(s2, 16 - t1);
                    Vector2f uv3 = v2(s1, 16 - t1);

                    // index (0,1,2) (0,2,3) 上
                    // index (0,2,1) (0,3,2) 下
                    int[] index = {0, 1, 2, 0, 2, 3};
                    Mesh mesh = new Mesh(
                            new Vector3f[]{v0, v1, v2, v3},
                            index,
                            new Vector2f[]{uv0, uv1, uv2, uv3},
                            new Vector3f[]{DOWN, DOWN, DOWN, DOWN},
                            new Vector4f[]{DARK, DARK, DARK, DARK});

                    String texture = getTexture(textures, face.getTexture());
                    Material material = makeMaterial(texture);
                    rootNode.attachChild(new Geometry(mesh, material));
                    break;
                }

                case "up": {
                    Vector3f v0 = v3(x1, y2, z1);
                    Vector3f v1 = v3(x1, y2, z2);
                    Vector3f v2 = v3(x2, y2, z2);
                    Vector3f v3 = v3(x2, y2, z1);

                    Vector2f uv0 = v2(s1, t1);
                    Vector2f uv1 = v2(s1, t2);
                    Vector2f uv2 = v2(s2, t2);
                    Vector2f uv3 = v2(s2, t1);

                    int[] index = {0, 1, 2, 0, 2, 3};
                    Mesh mesh = new Mesh(
                            new Vector3f[]{v0, v1, v2, v3},
                            index,
                            new Vector2f[]{uv0, uv1, uv2, uv3},
                            new Vector3f[]{UP, UP, UP, UP},
                            new Vector4f[]{LIGHT, LIGHT, LIGHT, LIGHT});

                    String texture = getTexture(textures, face.getTexture());
                    Material material = makeMaterial(texture);
                    rootNode.attachChild(new Geometry(mesh, material));
                    break;
                }
                case "north": {// FIXME
                    Vector3f v0 = v3(x2, y2, z1);
                    Vector3f v1 = v3(x2, y1, z1);
                    Vector3f v2 = v3(x1, y1, z1);
                    Vector3f v3 = v3(x1, y2, z1);

                    Vector2f uv0 = v2(s2, 16 - t2);
                    Vector2f uv1 = v2(s2, 16 - t1);
                    Vector2f uv2 = v2(s1, 16 - t1);
                    Vector2f uv3 = v2(s1, 16 - t2);

                    int[] index = {0, 1, 2, 0, 2, 3};
                    Mesh mesh = new Mesh(
                            new Vector3f[]{v0, v1, v2, v3},
                            index,
                            new Vector2f[]{uv0, uv1, uv2, uv3},
                            new Vector3f[]{NORTH, NORTH, NORTH, NORTH},
                            new Vector4f[]{LIGHT_GRAY, LIGHT_GRAY, LIGHT_GRAY, LIGHT_GRAY});

                    String texture = getTexture(textures, face.getTexture());
                    Material material = makeMaterial(texture);
                    rootNode.attachChild(new Geometry(mesh, material));
                    break;
                }
                case "south": {
                    Vector3f v0 = v3(x1, y2, z2);
                    Vector3f v1 = v3(x1, y1, z2);
                    Vector3f v2 = v3(x2, y1, z2);
                    Vector3f v3 = v3(x2, y2, z2);

                    Vector2f uv0 = v2(s1, 16 - t2);
                    Vector2f uv1 = v2(s2, 16 - t2);
                    Vector2f uv2 = v2(s2, 16 - t1);
                    Vector2f uv3 = v2(s1, 16 - t1);

                    int[] index = {0, 1, 2, 0, 2, 3};
                    Mesh mesh = new Mesh(
                            new Vector3f[]{v0, v1, v2, v3},
                            index,
                            new Vector2f[]{uv0, uv1, uv2, uv3},
                            new Vector3f[]{SOUTH, SOUTH, SOUTH, SOUTH},
                            new Vector4f[]{LIGHT_GRAY, LIGHT_GRAY, LIGHT_GRAY, LIGHT_GRAY});

                    String texture = getTexture(textures, face.getTexture());
                    Material material = makeMaterial(texture);
                    rootNode.attachChild(new Geometry(mesh, material));
                    break;
                }
                case "west": {// FIXME
                    Vector3f v0 = v3(x1, y2, z1);
                    Vector3f v1 = v3(x1, y1, z1);
                    Vector3f v2 = v3(x1, y1, z2);
                    Vector3f v3 = v3(x1, y2, z2);

                    Vector2f uv0 = v2(s1, 16 - t2);
                    Vector2f uv1 = v2(s1, 16 - t1);
                    Vector2f uv2 = v2(s2, 16 - t1);
                    Vector2f uv3 = v2(s2, 16 - t2);

                    int[] index = {0, 1, 2, 0, 2, 3};
                    Mesh mesh = new Mesh(
                            new Vector3f[]{v0, v1, v2, v3},
                            index,
                            new Vector2f[]{uv0, uv1, uv2, uv3},
                            new Vector3f[]{WEST, WEST, WEST, WEST},
                            new Vector4f[]{DARK_GRAY, DARK_GRAY, DARK_GRAY, DARK_GRAY});

                    String texture = getTexture(textures, face.getTexture());
                    Material material = makeMaterial(texture);
                    rootNode.attachChild(new Geometry(mesh, material));
                    break;
                }
                case "east": {
                    Vector3f v0 = v3(x2, y2, z2);
                    Vector3f v1 = v3(x2, y1, z2);
                    Vector3f v2 = v3(x2, y1, z1);
                    Vector3f v3 = v3(x2, y2, z1);

                    Vector2f uv0 = v2(s2, 16 - t2);
                    Vector2f uv1 = v2(s2, 16 - t1);
                    Vector2f uv2 = v2(s1, 16 - t1);
                    Vector2f uv3 = v2(s1, 16 - t2);

                    int[] index = {0, 1, 2, 0, 2, 3};
                    Mesh mesh = new Mesh(
                            new Vector3f[]{v0, v1, v2, v3},
                            index,
                            new Vector2f[]{uv0, uv1, uv2, uv3},
                            new Vector3f[]{EAST, EAST, EAST, EAST},
                            new Vector4f[]{DARK_GRAY, DARK_GRAY, DARK_GRAY, DARK_GRAY});

                    String texture = getTexture(textures, face.getTexture());
                    Material material = makeMaterial(texture);
                    rootNode.attachChild(new Geometry(mesh, material));
                    break;
                }
            }
        }
    }

    private Material makeMaterial(String texture) {
        BufferedImage img = assetLoader.loadTexture(texture);
        Image image = new Image(img);
        Texture diffuseMap = new Texture(image);
        diffuseMap.setMagFilter(Texture.MagFilter.NEAREST);

        Material material = new Material();
        material.setShader(new UnshadedShader());
        material.getRenderState().setAlphaTest(true);
        material.getRenderState().setAlphaFalloff(0.5f);
        material.setDiffuse(new Vector4f(1, 1, 1, 1));
        material.setDiffuseMap(diffuseMap);
        return material;
    }

    private String getTexture(Map<String, String> map, String id) {
        if (id.startsWith("#")) {
            return getTexture(map, id.substring(1));
        } else {
            return map.getOrDefault(id, "assets/minecraft/textures/block/missing.png");
        }
    }

}
