package top.wu949.dsbr.optimizer.gpu;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/** One upload buffer per ordered draw batch. Packing never changes a command's pose cursor. */
public final class PoseStaging implements AutoCloseable {
    private ByteBuffer data;
    public ByteBuffer prepare(int size) {
        if (size <= 0) throw new IllegalArgumentException("empty pose batch");
        if (data == null || data.capacity() < size) {
            close(); data = MemoryUtil.memAlloc(size);
        }
        data.clear().limit(size); return data;
    }
    public static void copy(ByteBuffer packed, int offset, ByteBuffer pose) {
        packed.put(offset, pose, 0, pose.capacity());
    }
    public long bytes() { return data == null ? 0 : data.capacity(); }
    public void close() { if (data != null) { MemoryUtil.memFree(data); data = null; } }
}
