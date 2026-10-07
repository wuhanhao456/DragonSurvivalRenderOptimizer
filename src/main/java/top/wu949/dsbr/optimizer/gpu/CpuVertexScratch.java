package top.wu949.dsbr.optimizer.gpu;

import org.joml.*;
import java.util.ArrayList;

/** Render-thread stack, including reentrant consumers. A caller owns its slot until return. */
public final class CpuVertexScratch {
    public static final CpuVertexScratch INSTANCE = new CpuVertexScratch();
    public static final class Slot {
        public final Matrix4f matrix = new Matrix4f();
        public final Vector3f normal = new Vector3f();
        public final Vector4f vertex = new Vector4f();
    }
    private final ArrayList<Slot> slots = new ArrayList<>();
    private int depth;
    public Slot acquire() {
        if (depth == slots.size()) slots.add(new Slot());
        return slots.get(depth++);
    }
    public void release(Slot slot) {
        if (depth == 0 || slots.get(depth - 1) != slot) throw new IllegalStateException("CPU scratch ownership mismatch");
        depth--;
        // Unusual deep consumers should not retain an unbounded scratch stack.
        if (depth == 0) while (slots.size() > 16) slots.removeLast();
    }
    public long bytes() { return slots.size() * 256L; }
    public void clear() {
        if (depth != 0) throw new IllegalStateException("CPU scratch still borrowed");
        slots.clear();
    }
}
