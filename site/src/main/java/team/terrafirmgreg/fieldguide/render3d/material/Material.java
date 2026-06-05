package team.terrafirmgreg.fieldguide.render3d.material;

import team.terrafirmgreg.fieldguide.render3d.math.Vector4f;

/**
 * Material payload consumed by {@link team.terrafirmgreg.fieldguide.export.GlTFExporter}.
 */
public class Material {

    private final RenderState renderState = new RenderState();
    private final Vector4f diffuse = new Vector4f(1f, 1f, 1f, 1f);
    private float shininess = 1f;
    private Texture diffuseMap;

    public RenderState getRenderState() {
        return renderState;
    }

    public Vector4f getDiffuse() {
        return diffuse;
    }

    public float getShininess() {
        return shininess;
    }

    public Texture getDiffuseMap() {
        return diffuseMap;
    }

    public void setDiffuseMap(Texture diffuseMap) {
        this.diffuseMap = diffuseMap;
    }
}
