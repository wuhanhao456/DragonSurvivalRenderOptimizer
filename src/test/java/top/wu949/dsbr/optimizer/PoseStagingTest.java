package top.wu949.dsbr.optimizer;

import org.junit.jupiter.api.Test;
import top.wu949.dsbr.optimizer.gpu.*;
import static org.junit.jupiter.api.Assertions.*;

class PoseStagingTest {
    @Test void commandsRetainIndependentDataAndCursorsAcrossPackedUpload() {
        try (var a = new PoseSnapshot(1); var b = new PoseSnapshot(1); var staging = new PoseStaging()) {
            a.data.putFloat(0, 4); b.data.putFloat(0, 7);
            a.data.position(3); b.data.position(5);
            var packed = staging.prepare(288);
            PoseStaging.copy(packed, 0, a.data); PoseStaging.copy(packed, 144, b.data);
            assertEquals(4, packed.getFloat(0)); assertEquals(7, packed.getFloat(144));
            assertEquals(3, a.data.position()); assertEquals(5, b.data.position());
            assertSame(packed, staging.prepare(144)); assertEquals(144, packed.limit());
            assertEquals(288, staging.bytes()); staging.close(); assertEquals(0, staging.bytes());
        }
    }
}
