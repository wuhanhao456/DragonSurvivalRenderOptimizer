package top.wu949.dsbr.optimizer.compat;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Function;

/** Optional discovery uses resources and names only; it never loads a Core class. */
public final class OptionalNpcAdapter {
    public static final String BASE = "com.zonlong.beloong.client.NpcRenderer";
    public static final String MO = "com.zonlong.beloong.client.MoRenderer";
    public static final String DIHUANG = "com.zonlong.beloong.client.DihuangLoongRenderer";
    // Bytecode reviewed for 0.10.1. A fork with the same version must also fail closed.
    public static final Map<String, String> AUDITED = Map.of(
        BASE, "4356b15275f742a8650811cc504fdf9b1517ce6d71dba2865ddbb9c46e7de0e4",
        MO, "7d614c2bd558bf87428f6494036c7acbab8d0d50a4ab35a820422bd4444c3036",
        DIHUANG, "587cbfb4b5fd6598b5171cc026f6b5aafeb13e954469b0758d2fe6c77bdacb41",
        "com.zonlong.beloong.entity.NpcEntity", "e77a89b7068a5ca1a4eb01bf47ed714719471e4dc365dbf60f33337d89587882",
        "com.zonlong.beloong.entity.MoEntity", "619aac7c53087aa8faee162c9e5f04155c083f99129b9903793ef7db2d15e501",
        "com.zonlong.beloong.entity.DihuangLoongEntity", "3944153a4a84c03772041584dcdc69a6a3d37a1e70bd2f34b593f6e78c2ab61e",
        "com.zonlong.beloong.client.model.MoModel", "8d9cdef0a40e0e8f3f84bf237ff2d90faaefc44cf762c2ed8c313d1184abeb23",
        "com.zonlong.beloong.client.model.DihuangLoongModel", "c7a3af1a358db2476af7c10d38f1e70e43ba551eadb553e1b4650141852eb2c8");
    public static boolean compatible;
    public static String status = "absent";
    public static void discover(String version, Function<String, InputStream> resources) {
        compatible = false;
        if (version == null) { status = "absent (DS remains enabled)"; return; }
        if (!version.equals("0.10.1")) { status = "unsupported Core version " + version; return; }
        try {
            for (var entry : AUDITED.entrySet()) {
                try (var in = resources.apply(entry.getKey().replace('.', '/') + ".class")) {
                    if (in == null || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(entry.getValue())) {
                        status = "unverified NPC interface: " + entry.getKey(); return;
                    }
                }
            }
            compatible = true; status = "Core 0.10.1 NPC interfaces verified";
        } catch (Exception | LinkageError e) { status = "NPC resource inspection failed: " + e.getClass().getSimpleName(); }
    }
    public static boolean renderer(Class<?> type) {
        if (!compatible || !(type.getName().equals(MO) || type.getName().equals(DIHUANG))) return false;
        for (var parent = type.getSuperclass(); parent != null; parent = parent.getSuperclass()) if (parent.getName().equals(BASE)) return true;
        return false;
    }
    private OptionalNpcAdapter() {}
}
