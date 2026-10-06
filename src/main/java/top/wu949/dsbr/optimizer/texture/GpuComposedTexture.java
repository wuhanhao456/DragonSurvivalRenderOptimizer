package top.wu949.dsbr.optimizer.texture;

import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.*;
import java.io.IOException;
import java.nio.file.Path;

/** DynamicTexture-compatible GPU storage. Pixel consumers trigger a single lazy readback. */
public final class GpuComposedTexture extends DynamicTexture {
    private NativeImage cpu;
    private int width, height;
    private boolean closed;
    private String diagnosticKey = "unregistered";
    public GpuComposedTexture() { super(1, 1, false); super.setPixels(null); }
    public GpuComposedTexture(String diagnosticKey) { this(); this.diagnosticKey = diagnosticKey; }
    public void copy(RenderTarget source) {
        RenderSystem.assertOnRenderThread();
        int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int oldRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            if (width != source.width || height != source.height) {
                width = source.width; height = source.height;
                TextureUtil.prepareImage(getId(), width, height);
            }
            RenderSystem.bindTexture(getId());
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
            if (cpu != null) { cpu.close(); cpu = null; }
            Diagnostics.INSTANCE.count(Diagnostics.Counter.COPY, diagnosticKey);
        } finally { GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead); RenderSystem.bindTexture(oldTexture); }
    }
    @Override public NativeImage getPixels() {
        if (closed) return null;
        RenderSystem.assertOnRenderThread();
        if (cpu == null) {
            int old = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); long start = System.nanoTime();
            cpu = new NativeImage(width, height, false);
            try { RenderSystem.bindTexture(getId()); cpu.downloadTexture(0, false); }
            finally { RenderSystem.bindTexture(old); }
            Diagnostics.INSTANCE.count(Diagnostics.Counter.READBACK, diagnosticKey);
            Diagnostics.INSTANCE.nanos("EXPLICIT_READBACK", System.nanoTime() - start, diagnosticKey);
        }
        return cpu;
    }
    @Override public void setPixels(NativeImage pixels) {
        if (cpu == pixels) return;
        if (cpu != null) cpu.close(); cpu = null;
        // DS uploadTexture closes its input after upload. Keep an owned image for editor callers.
        if (pixels != null) { cpu = new NativeImage(pixels.getWidth(), pixels.getHeight(), false); cpu.copyFrom(pixels); }
    }
    @Override public void upload() {
        if (cpu == null || closed) return;
        int old = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            if (width != cpu.getWidth() || height != cpu.getHeight()) { width = cpu.getWidth(); height = cpu.getHeight(); TextureUtil.prepareImage(getId(), width, height); }
            RenderSystem.bindTexture(getId()); cpu.upload(0, 0, 0, false);
        } finally { RenderSystem.bindTexture(old); }
    }
    @Override public void close() { if (closed) return; closed = true; if (cpu != null) { cpu.close(); cpu = null; } releaseId(); }
    @Override public void dumpContents(ResourceLocation id, Path path) throws IOException { var image = getPixels(); if (image != null) image.writeToFile(path.resolve(id.toDebugFileName() + ".png")); }
}
