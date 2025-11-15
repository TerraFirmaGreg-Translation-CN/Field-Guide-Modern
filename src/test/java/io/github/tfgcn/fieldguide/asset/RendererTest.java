package io.github.tfgcn.fieldguide.asset;

import io.github.jmecn.draw3d.Application;
import io.github.jmecn.draw3d.light.AmbientLight;
import io.github.jmecn.draw3d.light.DirectionalLight;
import io.github.jmecn.draw3d.material.Material;
import io.github.jmecn.draw3d.material.RenderState;
import io.github.jmecn.draw3d.material.Texture;
import io.github.jmecn.draw3d.math.Vector2f;
import io.github.jmecn.draw3d.math.Vector3f;
import io.github.jmecn.draw3d.math.Vector4f;
import io.github.jmecn.draw3d.renderer.Camera;
import io.github.jmecn.draw3d.renderer.Image;
import io.github.jmecn.draw3d.scene.Geometry;
import io.github.jmecn.draw3d.scene.Mesh;
import io.github.jmecn.draw3d.scene.Node;
import io.github.jmecn.draw3d.shader.UnshadedShader;
import io.github.tfgcn.fieldguide.exception.AssetNotFoundException;
import io.github.tfgcn.fieldguide.mc.BlockModel;
import io.github.tfgcn.fieldguide.mc.ElementFace;
import io.github.tfgcn.fieldguide.mc.ModelElement;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * desc:
 *
 * @author yanmaoyuan
 */
@Slf4j
public class RendererTest extends Application {

    public static void main(String[] args) {
        RendererTest app = new RendererTest();
        app.start();
    }
    //String model = "beneath:block/unposter";
    //String model = "tfc:block/metal/anvil/bismuth_bronze";
    String model = "firmalife:block/plant/pineapple_bush_2";// TODO 需要处理模型旋转
    //String model = "create:block/mechanical_pump/item";// TODO 模型不正常，需要处理uv旋转
    //String model = "gtceu:block/machine/hv_chemical_reactor";// TODO 需要处理变体
    //String model = "createaddition:block/electric_motor/block";// TODO cullface, face.rotaton, element.name, element.rotation
    //String model = "create:block/steam_engine/block";
    //String model = "tfc:block/wattle/unstained_wattle";
    //String model = "beneath:block/blackstone_aqueduct_base";

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

    private AssetLoader assetLoader;

    public RendererTest() {
        this.width = 512;
        this.height = 512;
        this.title = model;
    }

    @Override
    protected void initialize() {

        String modpackPath = "Modpack-Modern";
        assetLoader = new AssetLoader(Paths.get(modpackPath));

        Node node = buildModel(model);

        rootNode.attachChild(node);

        // light
        AmbientLight ambientLight = new AmbientLight(new Vector4f(0.7f));
        lights.add(ambientLight);

        DirectionalLight dirLight = new DirectionalLight(new Vector4f(0.3f), new Vector3f(-1f, -0.5f, -2f).normalizeLocal());
        lights.add(dirLight);

        Camera cam = getCamera();

        // parallel
        //cam.setParallel(-16f, 16, -16, 16, -1000f, 1000f);
        //cam.lookAt(new Vector3f(100, 100, 100), new Vector3f(0, 0, 0), Vector3f.UNIT_Y);

        cam.setLocation(new Vector3f(32f, 32f, 32f));
        cam.setDirection(new Vector3f(-1f, -1f, -1f).normalizeLocal());
    }

    @Override
    protected void update(float delta) {
    }

    double v3_scale = 1.0;

    private Vector3f v3(double x, double y, double z) {
        return new Vector3f((float)(x * v3_scale), (float)(y * v3_scale), (float)(z * v3_scale));
    }

    private Vector2f v2(double s, double t) {
        return new Vector2f((float) (s / 16.0), (float) (1.0 - t / 16.0));
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

            System.out.printf("%s: %d, cull %s\n", dir, face.getRotation(), face.getCullface());
            Mesh mesh;
            switch (dir) {
                case "down": {
                    Vector3f v0 = v3(x1, y1, z2);
                    Vector3f v1 = v3(x1, y1, z1);
                    Vector3f v2 = v3(x2, y1, z1);
                    Vector3f v3 = v3(x2, y1, z2);
                    mesh = new Mesh(
                            new Vector3f[]{v0, v1, v2, v3},
                            index,
                            texCoords,
                            new Vector3f[]{DOWN, DOWN, DOWN, DOWN},
                            new Vector4f[]{DARK, DARK, DARK, DARK});
                    break;
                }

                case "up": {
                    Vector3f v0 = v3(x1, y2, z1);
                    Vector3f v1 = v3(x1, y2, z2);
                    Vector3f v2 = v3(x2, y2, z2);
                    Vector3f v3 = v3(x2, y2, z1);

                    mesh = new Mesh(
                            new Vector3f[]{v0, v1, v2, v3},
                            index,
                            texCoords,
                            new Vector3f[]{UP, UP, UP, UP},
                            new Vector4f[]{LIGHT, LIGHT, LIGHT, LIGHT});
                    break;
                }
                case "north": {
                    Vector3f v0 = v3(x2, y2, z1);
                    Vector3f v1 = v3(x2, y1, z1);
                    Vector3f v2 = v3(x1, y1, z1);
                    Vector3f v3 = v3(x1, y2, z1);

                    mesh = new Mesh(
                            new Vector3f[]{v0, v1, v2, v3},
                            index,
                            texCoords,
                            new Vector3f[]{NORTH, NORTH, NORTH, NORTH},
                            new Vector4f[]{LIGHT_GRAY, LIGHT_GRAY, LIGHT_GRAY, LIGHT_GRAY});
                    break;
                }
                case "south": {
                    Vector3f v0 = v3(x1, y2, z2);
                    Vector3f v1 = v3(x1, y1, z2);
                    Vector3f v2 = v3(x2, y1, z2);
                    Vector3f v3 = v3(x2, y2, z2);

                    mesh = new Mesh(
                            new Vector3f[]{v0, v1, v2, v3},
                            index,
                            texCoords,
                            new Vector3f[]{SOUTH, SOUTH, SOUTH, SOUTH},
                            new Vector4f[]{LIGHT_GRAY, LIGHT_GRAY, LIGHT_GRAY, LIGHT_GRAY});
                    break;
                }
                case "west": {
                    Vector3f v0 = v3(x1, y2, z1);
                    Vector3f v1 = v3(x1, y1, z1);
                    Vector3f v2 = v3(x1, y1, z2);
                    Vector3f v3 = v3(x1, y2, z2);

                    mesh = new Mesh(
                            new Vector3f[]{v0, v1, v2, v3},
                            index,
                            texCoords,
                            new Vector3f[]{WEST, WEST, WEST, WEST},
                            new Vector4f[]{DARK_GRAY, DARK_GRAY, DARK_GRAY, DARK_GRAY});
                    break;
                }
                case "east": {
                    Vector3f v0 = v3(x2, y2, z2);
                    Vector3f v1 = v3(x2, y1, z2);
                    Vector3f v2 = v3(x2, y1, z1);
                    Vector3f v3 = v3(x2, y2, z1);

                    mesh = new Mesh(
                            new Vector3f[]{v0, v1, v2, v3},
                            index,
                            texCoords,
                            new Vector3f[]{EAST, EAST, EAST, EAST},
                            new Vector4f[]{DARK_GRAY, DARK_GRAY, DARK_GRAY, DARK_GRAY});
                    break;
                }
                default: {
                    return;
                }
            }

            String texture = getTexture(textures, face.getTexture());
            Material material = makeMaterial(texture);
            rootNode.attachChild(new Geometry(mesh, material));
        }
    }

}
