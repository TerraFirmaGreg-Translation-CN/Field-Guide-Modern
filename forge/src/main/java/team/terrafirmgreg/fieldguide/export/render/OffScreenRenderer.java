/*
 * Adapted from GuideME (Applied Energistics) — guideme.internal.siteexport.OffScreenRenderer
 * https://github.com/AppliedEnergistics/GuideME
 */
package team.terrafirmgreg.fieldguide.export.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL12;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

public class OffScreenRenderer implements AutoCloseable {

    private final NativeImage nativeImage;
    private final TextureTarget fb;
    private final int width;
    private final int height;

    public OffScreenRenderer(int width, int height) {
        this.width = width;
        this.height = height;
        RenderSystem.viewport(0, 0, width, height);
        nativeImage = new NativeImage(width, height, true);
        fb = new TextureTarget(width, height, true, true);
        fb.setClearColor(0, 0, 0, 0);
        fb.clear(true);
    }

    @Override
    public void close() {
        nativeImage.close();
        fb.destroyBuffers();

        var minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            var window = minecraft.getWindow();
            RenderSystem.viewport(0, 0, window.getWidth(), window.getHeight());
        }
    }

    public byte[] captureAsPng(Runnable r) {
        renderToBuffer(r);
        try {
            return nativeImage.asByteArray();
        } catch (IOException e) {
            throw new RuntimeException("failed to encode image as PNG", e);
        }
    }

    /** Copies the last rendered frame into a larger atlas image (no PNG round-trip). */
    public void copyPixelsTo(NativeImage target, int destX, int destY) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                target.setPixelRGBA(destX + x, destY + y, nativeImage.getPixelRGBA(x, y));
            }
        }
    }

    public void captureAsPng(Runnable r, Path path) throws IOException {
        renderToBuffer(r);
        nativeImage.writeToFile(path);
    }

    public boolean isAnimated(Collection<TextureAtlasSprite> sprites) {
        return sprites.stream().anyMatch(s -> s.contents().animatedTexture != null);
    }

    /** Binds atlases and resets animated sprites to frame 0 before a static PNG capture. */
    public void uploadAnimatedFirstFrame(Collection<TextureAtlasSprite> sprites) {
        var textureManager = Minecraft.getInstance().getTextureManager();
        for (var sprite : sprites) {
            if (sprite.contents().animatedTexture != null) {
                textureManager.getTexture(sprite.atlasLocation()).bind();
                sprite.uploadFirstFrame();
            }
        }
    }

    private void renderToBuffer(Runnable r) {
        fb.bindWrite(true);
        GlStateManager._clear(GL12.GL_COLOR_BUFFER_BIT | GL12.GL_DEPTH_BUFFER_BIT, false);
        r.run();
        fb.unbindWrite();

        fb.bindRead();
        nativeImage.downloadTexture(0, false);
        nativeImage.flipY();
        fb.unbindRead();
    }

    public void setupFlatGuiRendering() {
        var matrix4f = new Matrix4f().setOrtho(
                0.0f, 16,
                16, 0.0f,
                1000.0f, 21000.0f);
        RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);

        var poseStack = RenderSystem.getModelViewStack();
        poseStack.setIdentity();
        poseStack.translate(0.0f, 0.0f, -11000.0f);
        RenderSystem.applyModelViewMatrix();
        Lighting.setupForFlatItems();
        FogRenderer.setupNoFog();
    }

    public void setupItemRendering() {
        var matrix4f = new Matrix4f().setOrtho(
                0.0f, 16,
                16, 0.0f,
                1000.0f, 21000.0f);
        RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);

        var poseStack = RenderSystem.getModelViewStack();
        poseStack.setIdentity();
        poseStack.translate(0.0f, 0.0f, -11000.0f);
        RenderSystem.applyModelViewMatrix();
        Lighting.setupFor3DItems();
        FogRenderer.setupNoFog();
    }

    /**
     * GL state for EMI recipe panels — matches {@code dev.emi.emi.runtime.EmiScreenshotRecorder}.
     *
     * @param logicalWidth  EMI panel width in GUI pixels (not framebuffer pixels)
     * @param logicalHeight EMI panel height in GUI pixels
     */
    public void runWithEmiRecipeMatrices(int logicalWidth, int logicalHeight, Runnable draw) {
        Matrix4f backupProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        PoseStack view = RenderSystem.getModelViewStack();
        view.pushPose();
        view.setIdentity();
        view.translate(-1.0f, 1.0f, 0.0f);
        view.scale(2.0f / logicalWidth, -2.0f / logicalHeight, -0.001f);
        view.translate(0.0f, 0.0f, 10.0f);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().identity(), VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.viewport(0, 0, width, height);
        FogRenderer.setupNoFog();
        try {
            draw.run();
        } finally {
            RenderSystem.setProjectionMatrix(backupProj, VertexSorting.ORTHOGRAPHIC_Z);
            view.popPose();
            RenderSystem.applyModelViewMatrix();
            var window = Minecraft.getInstance().getWindow();
            RenderSystem.viewport(0, 0, window.getWidth(), window.getHeight());
        }
    }
}
