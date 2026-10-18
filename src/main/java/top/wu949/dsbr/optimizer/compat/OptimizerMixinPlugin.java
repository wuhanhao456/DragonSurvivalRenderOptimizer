package top.wu949.dsbr.optimizer.compat;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Decides which mixin groups apply by checking untransformed bytecode, without initializing target classes.
 * No mod version is pinned; only the contracts in {@link Contracts} gate each group.
 */
public final class OptimizerMixinPlugin implements IMixinConfigPlugin {
    public static boolean texturesCompatible, gpuCompatible, irisCompatible, soulCompatible, lowEndCompatible, rerenderCompatible;
    public static String status = "not checked";
    private static boolean geometryContracts;

    @Override
    public void onLoad(String pkg) {
        var log = org.slf4j.LoggerFactory.getLogger("RenderOptimizer");
        if (!FMLEnvironment.dist.isClient()) {
            status = "server: no client patches";
            return;
        }
        var mods = LoadingModList.get();
        var versions = new TreeMap<String, String>();
        if (mods != null) mods.getMods().forEach(m -> versions.put(m.getModId(), m.getVersion().toString()));

        var cache = new HashMap<String, Optional<ClassNode>>();
        Function<String, ClassNode> loader = name -> cache.computeIfAbsent(name, OptimizerMixinPlugin::read).orElse(null);
        var problems = new ArrayList<String>();

        // The standalone predecessor patches the same DS methods; running both would double-wrap them.
        boolean duplicate = versions.containsKey("beloong_render_optimizer");
        texturesCompatible = !duplicate && check(loader, Contracts.TEXTURE_GROUP, problems);
        soulCompatible = texturesCompatible && check(loader, Contracts.SOUL_GROUP, problems);
        lowEndCompatible = soulCompatible && check(loader, Contracts.LOW_END_GROUP, problems);
        geometryContracts = texturesCompatible && check(loader, Contracts.GPU_GROUP, problems);
        gpuCompatible = geometryContracts;
        // Do not combine competing geometry backends.
        boolean otherBackend = versions.keySet().stream().anyMatch(id -> !id.equals("dsbr") && id.contains("bedrockrenderer"));
        if (otherBackend) gpuCompatible = false;
        if (versions.containsKey("iris")) {
            irisCompatible = check(loader, Contracts.IRIS_GROUP, problems);
            gpuCompatible &= irisCompatible;
        }
        if (gpuCompatible) {
            var missing = Contracts.rerender(loader);
            problems.addAll(missing);
            rerenderCompatible = missing.isEmpty();
        }
        OptionalNpcAdapter.discover(versions.get("beloong"), name -> MixinService.getService().getResourceAsStream(name));

        var relevant = Set.of("dragonsurvival", "geckolib", "iris", "sodium", "dsbr", "beloong");
        var untested = Contracts.VALIDATED.entrySet().stream()
                .filter(e -> versions.containsKey(e.getKey()) && !e.getValue().equals(versions.get(e.getKey())))
                .map(e -> e.getKey() + "=" + versions.get(e.getKey()))
                .sorted()
                .toList();
        status = "versions=" + versions.entrySet().stream().filter(e -> relevant.contains(e.getKey())).toList()
                + "; texture=" + texturesCompatible + "; GPU=" + gpuCompatible + "; Iris=" + irisCompatible + "; souls=" + soulCompatible
                + "; lowEnd=" + lowEndCompatible + "; rerenderReuse=" + rerenderCompatible + "; NPC=" + OptionalNpcAdapter.status
                + (otherBackend ? "; other bedrock renderer detected: GPU disabled" : "")
                + (duplicate ? "; duplicate standalone optimizer detected: integrated patches disabled" : "")
                + (untested.isEmpty() ? "" : "; untested=" + untested)
                + (problems.isEmpty() ? "" : "; missing=" + problems);
        log.info(status);
        if (!untested.isEmpty()) log.warn("Untested mod versions {}; patches are gated by bytecode contracts only", untested);
        if (!problems.isEmpty()) log.warn("Patches disabled because their targets changed or are absent: {}", problems);
    }

    private static boolean check(Function<String, ClassNode> loader, Contracts.Group group, List<String> problems) {
        var missing = Contracts.check(loader, group);
        problems.addAll(missing);
        return missing.isEmpty();
    }

    private static Optional<ClassNode> read(String name) {
        try (var stream = MixinService.getService().getResourceAsStream(name.replace('.', '/') + ".class")) {
            return stream == null ? Optional.empty() : Optional.of(Contracts.read(stream.readAllBytes()));
        } catch (Exception | LinkageError e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        if (mixin.endsWith("LowEndAnimationMixin") || mixin.endsWith("LowEndLayersMixin") || mixin.endsWith("SoulIndicatorMixin")) return lowEndCompatible;
        if (mixin.endsWith("SoulRendererMixin")) return texturesCompatible && soulCompatible;
        if (mixin.endsWith("IrisBufferSourceMixin")) return gpuCompatible && irisCompatible;
        // Low-end support reuses the CPU geometry hooks, which need the same GeckoLib targets as the GPU path.
        if (mixin.endsWith("GeoRendererMixin")) return geometryContracts && (gpuCompatible || lowEndCompatible);
        if (mixin.endsWith("BufferSourceMixin") || mixin.endsWith("DragonGlowMixin") || mixin.endsWith("GameRendererScopeMixin")) return gpuCompatible;
        return texturesCompatible;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
}
