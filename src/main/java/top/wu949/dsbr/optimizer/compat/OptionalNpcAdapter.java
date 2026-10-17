package top.wu949.dsbr.optimizer.compat;

import org.objectweb.asm.ClassReader;

import java.io.InputStream;
import java.util.List;
import java.util.function.Function;

/**
 * Optional discovery uses resources and names only; it never loads a Core class. Any Core version is accepted
 * when the classes the adapter relies on are present with the expected hierarchy. Geometry that differs from
 * GeckoLib's default is still rejected per renderer at runtime (see {@link RendererAdmission}).
 */
public final class OptionalNpcAdapter {
    public static final String BASE = "com.zonlong.beloong.client.NpcRenderer";
    public static final String MO = "com.zonlong.beloong.client.MoRenderer";
    public static final String DIHUANG = "com.zonlong.beloong.client.DihuangLoongRenderer";
    /** Classes matched by name in the GPU and low-end paths. */
    public static final List<String> REQUIRED = List.of(BASE, MO, DIHUANG,
            "com.zonlong.beloong.entity.NpcEntity", "com.zonlong.beloong.entity.MoEntity", "com.zonlong.beloong.entity.DihuangLoongEntity",
            "com.zonlong.beloong.client.model.MoModel", "com.zonlong.beloong.client.model.DihuangLoongModel");
    public static boolean compatible;
    public static String status = "absent";

    public static void discover(String version, Function<String, InputStream> resources) {
        compatible = false;
        if (version == null) {
            status = "absent (DS remains enabled)";
            return;
        }
        try {
            for (var name : REQUIRED) {
                try (var in = resources.apply(name.replace('.', '/') + ".class")) {
                    if (in == null) {
                        status = "Core " + version + ": missing " + name;
                        return;
                    }
                    var reader = new ClassReader(in.readAllBytes());
                    if ((name.equals(MO) || name.equals(DIHUANG)) && !reader.getSuperName().equals(BASE.replace('.', '/'))) {
                        status = "Core " + version + ": " + name + " no longer extends NpcRenderer";
                        return;
                    }
                }
            }
            compatible = true;
            status = "Core " + version + " NPC classes found";
        } catch (Exception | LinkageError e) {
            status = "NPC resource inspection failed: " + e.getClass().getSimpleName();
        }
    }

    public static boolean renderer(Class<?> type) {
        if (!compatible || !(type.getName().equals(MO) || type.getName().equals(DIHUANG))) return false;
        for (var parent = type.getSuperclass(); parent != null; parent = parent.getSuperclass()) if (parent.getName().equals(BASE)) return true;
        return false;
    }

    private OptionalNpcAdapter() {}
}
