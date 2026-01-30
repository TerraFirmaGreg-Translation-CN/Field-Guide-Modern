package team.terrafirmgreg.fieldguide.asset;

import team.terrafirmgreg.fieldguide.render.BaseModelBuilder;
import team.terrafirmgreg.fieldguide.render3d.Application;
import team.terrafirmgreg.fieldguide.render3d.math.Matrix4f;
import team.terrafirmgreg.fieldguide.render3d.math.Vector3f;
import team.terrafirmgreg.fieldguide.render3d.renderer.Camera;
import team.terrafirmgreg.fieldguide.render3d.scene.Node;
import team.terrafirmgreg.fieldguide.data.minecraft.blockmodel.ElementRotation;
import team.terrafirmgreg.fieldguide.data.minecraft.blockmodel.ModelElement;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Paths;

import static team.terrafirmgreg.fieldguide.render.BaseModelBuilder.SCALE;
import static team.terrafirmgreg.fieldguide.render.BaseModelBuilder.v3;

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

    String model = "beneath:block/unposter";
    //String model = "tfc:block/metal/anvil/bismuth_bronze";
    //String model = "firmalife:block/plant/pineapple_bush_2";
    //String model = "create:block/mechanical_pump/block";
    //String model = "create:block/mechanical_pump/item";
    //String model = "create:block/mechanical_pump/cog";
    //String model = "gtceu:block/machine/hv_chemical_reactor";// TODO 需要处理变体
    //String model = "createaddition:block/electric_motor/block";// TODO 纹理坐标映射不正确
    //String model = "create:block/steam_engine/block";// TODO 纹理坐标映射不正确
    //String model = "tfc:block/wattle/unstained_wattle";
    //String model = "beneath:block/blackstone_aqueduct_base";
    //String model = "tfc:block/blast_furnace/unlit";

    private AssetLoader assetLoader;
    private BaseModelBuilder modelBuilder;

    public RendererTest() {
        this.width = 512;
        this.height = 512;
        this.title = "Block Model Preview";
    }

    @Override
    protected void initialize() {

        String modpackPath = "Modpack-Modern";
        assetLoader = new AssetLoader(Paths.get(modpackPath));
        modelBuilder = new BaseModelBuilder(assetLoader);

        Node node = modelBuilder.buildModel(model);
        rootNode.attachChild(node);

        Camera cam = getCamera();

        // parallel
        cam.setParallel(-11 * SCALE, 11 * SCALE, -11 * SCALE, 11 * SCALE, -1000f, 1000f);

        cam.lookAt(v3(32f, 32f, -16f), v3(8f, 8f, 8f), Vector3f.UNIT_Y);
    }

    @Override
    protected void update(float delta) {
    }

    public void rotation(ModelElement element) {

        double o0 = (element.getFrom()[0] + element.getTo()[0]) * 0.5;
        double o1 = (element.getFrom()[1] + element.getTo()[1]) * 0.5;
        double o2 = (element.getFrom()[2] + element.getTo()[2]) * 0.5;

        ElementRotation rotation = element.getRotation();
        Matrix4f rot;
        if (rotation != null) {
            double[] origin = rotation.getOrigin();
            if (origin != null && origin.length == 3) {
                o0 = origin[0];
                o1 = origin[1];
                o2 = origin[2];
            } else {
                log.warn("origin is null, element: {}", element);
            }

            if (rotation.getAxis() != null && rotation.getAngle() != null && rotation.getAngle() != 0) {
                String axis = rotation.getAxis();
                double angle = rotation.getAngle();

                switch(axis) {
                    case "x": {
                        rot = new Matrix4f().fromRotateX((float) angle);
                        break;
                    }
                    case "y": {
                        rot = new Matrix4f().fromRotateY((float) angle);
                        break;
                    }
                    case "z": {
                        rot = new Matrix4f().fromRotateZ((float) angle);
                        break;
                    }
                    default: {
                        log.error("Unknown axis: {}", axis);
                        rot = null;
                        break;
                    }
                }

                if (rot != null) {
                    Matrix4f translate = new Matrix4f().initTranslation((float) o0, (float) o1, (float) o2);
                    Matrix4f translateBack = new Matrix4f().initTranslation(-(float) o0, -(float) o1, -(float) o2);
                    Matrix4f transform = translateBack.mult(rot).mult(translate);
                    // TODO
                }
            } else {
                log.warn("axis or angle is null, element: {}", element);
            }
        }
    }
}
