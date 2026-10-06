package top.wu949.dsbr.optimizer.gpu;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.BufferUploader;
import org.lwjgl.opengl.*;

/** Compute and VAO work must not desynchronize Minecraft's program/texture state caches. */
public final class GlBindings implements AutoCloseable {
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
    private final int array = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    private final int storage = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
    private final int[] indexed = new int[3];
    private final long[] start = new long[3], size = new long[3];
    public GlBindings() {
        for (int i = 0; i < 3; i++) {
            indexed[i] = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, i);
            start[i] = GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START, i);
            size[i] = GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE, i);
        }
    }
    public void close() {
        // Raw glUseProgram during compute leaves GlStateManager's cached value at the old program.
        GL20.glUseProgram(program); GlStateManager._glUseProgram(program);
        GlStateManager._glBindVertexArray(vao); GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, array);
        for (int i = 0; i < 3; i++) {
            if (indexed[i] != 0 && size[i] > 0) GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, i, indexed[i], start[i], size[i]);
            else GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, i, indexed[i]);
        }
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, storage); BufferUploader.invalidate();
    }
}
