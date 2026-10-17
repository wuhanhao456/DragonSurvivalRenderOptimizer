package top.wu949.dsbr.optimizer;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import top.wu949.dsbr.optimizer.compat.OptionalNpcAdapter;
import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class OptionalNpcAdapterTest {
    private static byte[] type(String resource, String superName) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, resource.substring(0, resource.length() - ".class".length()), null, superName, null);
        writer.visitEnd();
        return writer.toByteArray();
    }
    private static String parent(String resource) {
        boolean renderer = resource.equals(OptionalNpcAdapter.MO.replace('.', '/') + ".class") || resource.equals(OptionalNpcAdapter.DIHUANG.replace('.', '/') + ".class");
        return renderer ? OptionalNpcAdapter.BASE.replace('.', '/') : "java/lang/Object";
    }
    @Test void absentCoreNeverAttemptsClassDiscovery() {
        var calls = new AtomicInteger();
        OptionalNpcAdapter.discover(null, name -> { calls.incrementAndGet(); throw new AssertionError(); });
        assertFalse(OptionalNpcAdapter.compatible); assertEquals(0, calls.get());
    }
    @Test void anyCoreVersionWithTheExpectedClassesIsAccepted() {
        for (var version : new String[]{"0.10.1", "0.11.0", "1.0.0-beta"}) {
            OptionalNpcAdapter.discover(version, name -> new ByteArrayInputStream(type(name, parent(name))));
            assertTrue(OptionalNpcAdapter.compatible, OptionalNpcAdapter.status);
        }
    }
    @Test void missingClassesOrChangedHierarchyDisableOnlyNpcAdapter() {
        OptionalNpcAdapter.discover("0.10.1", name -> null);
        assertFalse(OptionalNpcAdapter.compatible); assertTrue(OptionalNpcAdapter.status.contains("missing"));
        OptionalNpcAdapter.discover("0.12.0", name -> new ByteArrayInputStream(type(name, "java/lang/Object")));
        assertFalse(OptionalNpcAdapter.compatible); assertTrue(OptionalNpcAdapter.status.contains("NpcRenderer"));
        OptionalNpcAdapter.discover("0.12.0", name -> new ByteArrayInputStream(new byte[]{1, 2, 3}));
        assertFalse(OptionalNpcAdapter.compatible); assertTrue(OptionalNpcAdapter.status.contains("inspection failed"));
    }
}
