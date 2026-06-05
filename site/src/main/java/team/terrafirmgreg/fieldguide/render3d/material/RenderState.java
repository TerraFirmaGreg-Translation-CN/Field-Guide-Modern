package team.terrafirmgreg.fieldguide.render3d.material;

/**
 * Alpha/blend hints for glTF material export.
 */
public class RenderState {

    public enum CullMode {
        NEVER,
        FACE,
        BACK,
        ALWAYS
    }

    public enum BlendMode {
        OFF,
        ADD,
        ALPHA_BLEND
    }

    private CullMode cullMode = CullMode.BACK;
    private boolean alphaTest;
    private float alphaFalloff;
    private BlendMode blendMode = BlendMode.OFF;

    public CullMode getCullMode() {
        return cullMode;
    }

    public void setCullMode(CullMode cullMode) {
        this.cullMode = cullMode;
    }

    public boolean isAlphaTest() {
        return alphaTest;
    }

    public void setAlphaTest(boolean alphaTest) {
        this.alphaTest = alphaTest;
    }

    public float getAlphaFalloff() {
        return alphaFalloff;
    }

    public void setAlphaFalloff(float alphaFalloff) {
        this.alphaFalloff = alphaFalloff;
    }

    public BlendMode getBlendMode() {
        return blendMode;
    }

    public void setBlendMode(BlendMode blendMode) {
        this.blendMode = blendMode;
    }
}
