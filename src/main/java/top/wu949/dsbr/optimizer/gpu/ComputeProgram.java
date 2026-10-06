package top.wu949.dsbr.optimizer.gpu;

import org.lwjgl.opengl.*;
import java.nio.charset.StandardCharsets;

public final class ComputeProgram implements AutoCloseable {
    private int program;
    public int id() { return program; }
    public void compile() throws java.io.IOException {
        if (program != 0) return;
        String source;
        try (var in = ComputeProgram.class.getResourceAsStream("/assets/dsbr/shaders/transform.comp")) {
            if (in == null) throw new java.io.IOException("missing compute shader"); source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
        int candidate = GL20.glCreateProgram();
        try {
            GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));
            GL20.glAttachShader(candidate, shader); GL20.glLinkProgram(candidate);
            if (GL20.glGetProgrami(candidate, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException(GL20.glGetProgramInfoLog(candidate));
            program = candidate; candidate = 0;
        } finally { GL20.glDeleteShader(shader); if (candidate != 0) GL20.glDeleteProgram(candidate); }
    }
    public void dispatch(int mesh, int pose, int output, int quads, IrisIds ids, int stride) {
        GL20.glUseProgram(program);
        GL30.glUniform1ui(GL20.glGetUniformLocation(program, "quadCount"), quads);
        GL30.glUniform1ui(GL20.glGetUniformLocation(program, "stride"), stride);
        GL30.glUniform3ui(GL20.glGetUniformLocation(program, "entityIds"), ids.entity, ids.block, ids.item);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "recalculateNormal"), ids.recalculateNormal ? 1 : 0);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, mesh); GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, pose); GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, output);
        GL43.glDispatchCompute((quads + 63) / 64, 1, 1);
        GL42.glMemoryBarrier(GL43.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT | GL43.GL_SHADER_STORAGE_BARRIER_BIT);
    }
    public record IrisIds(int entity, int block, int item, boolean recalculateNormal) {}
    public void close() { if (program != 0) GL20.glDeleteProgram(program); program = 0; }
}
