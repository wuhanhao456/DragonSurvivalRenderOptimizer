package top.wu949.dsbr.optimizer.validation;

import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import top.wu949.dsbr.optimizer.*;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import top.wu949.dsbr.optimizer.gpu.GpuDispatcher;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Short startup/world/visible-model check requested after the branding-only rebuild. */
@EventBusSubscriber(modid = "dsbr_validation", value = Dist.CLIENT)
public final class SmokeProbe {
    private static final long START = System.nanoTime();
    private static final List<String> failures = new ArrayList<>();
    private static final Map<String,Object> result = new LinkedHashMap<>();
    private static int stage, ticks;
    private static boolean done;
    private static CompletableFuture<Void> setup;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("beloongrender.smoke") || done) return;
        var mc = Minecraft.getInstance();
        try {
            if (!mc.gameDirectory.getName().startsWith("dsbr-validation-smoke-")) throw new IllegalStateException("Isolated smoke directory required");
            if (System.nanoTime()-START > 900_000_000_000L) throw new IllegalStateException("Smoke startup deadline exceeded");
            if(stage==0) {
                if(!(mc.screen instanceof TitleScreen))return;
                if(++ticks<20)return;
                var info=ModList.get().getModContainerById("dsbr").orElseThrow().getModInfo();
                result.put("displayName",info.getDisplayName());result.put("version",info.getVersion().toString());result.put("modId",info.getModId());
                check(info.getDisplayName().equals("Dragon Survival Render Optimizer"),"Unexpected mod display name");
                result.put("titleReached",true);screenshot(mc,"smoke-title");
                if(!Files.isDirectory(mc.gameDirectory.toPath().resolve("saves/single-round")))throw new IllegalStateException("Isolated fixture world missing");
                stage=1;ticks=0;
                mc.createWorldOpenFlows().openWorld("single-round",()->{throw new IllegalStateException("Smoke world failed to open");});return;
            }
            if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            ticks++;
            if(stage==1) {
                if(setup==null){setup=SingleRoundProbe.setModel(mc,0);return;}
                if(!setup.isDone()||ticks<100)return;setup.join();
                mc.setScreen(null);mc.setCameraEntity(mc.player);mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.options.pauseOnLostFocus=false;mc.options.enableVsync().set(false);mc.options.renderDistance().set(6);
                mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(0);mc.player.xRotO=0;
                mc.player.setYHeadRot(0);mc.player.yHeadRotO=0;mc.player.setYBodyRot(0);mc.player.yBodyRotO=0;
                SingleRoundProbe.souls(mc,4,0,"idle",true);Diagnostics.INSTANCE.reset();AnimationSamples.begin("smoke/visible-idle");
                stage=2;ticks=0;result.put("worldReached",true);return;
            }
            if(stage==2&&ticks>=120) {
                screenshot(mc,"smoke-world");var samples=AnimationSamples.snapshot();
                var players=samples.stream().filter(s->s.get("actor").equals("player")).toList();
                check(players.size()>1,"No visible player animation samples");
                check(players.stream().map(s->s.get("tick")).distinct().count()>1,"Player animation time did not advance");
                check(players.stream().map(s->s.get("poseHash")).distinct().count()>1,"Player idle pose did not advance");
                result.put("animationSamples",samples);var stats=Diagnostics.INSTANCE.snapshot();result.put("worldStats",stats);
                var totals=(Map<?,?>)stats.get("totals");check(number(totals,"GPU_PASS")>0,"No GPU dragon rendering");
                check(number(totals,"SOUL_GPU_PASS")>0,"No GPU soul rendering");check(GpuDispatcher.enabled(),"GPU disabled during world check");
                check(OptimizerConfig.MODE.get()==OptimizerConfig.Mode.GPU,"Unexpected startup mode");
                mc.gameMode.setLocalMode(GameType.SURVIVAL);Diagnostics.INSTANCE.reset();mc.setScreen(new InventoryScreen(mc.player));
                stage=3;ticks=0;return;
            }
            if(stage==3&&ticks>=60) {
                screenshot(mc,"smoke-inventory");result.put("inventoryScreen",mc.screen.getClass().getName());
                var stats=Diagnostics.INSTANCE.snapshot();result.put("inventoryStats",stats);
                check(mc.screen instanceof InventoryScreen,"Inventory redirected away from entity preview");
                check(number((Map<?,?>)stats.get("totals"),"GUI_CPU_PASS")>0,"Inventory entity preview did not render through CPU");
                mc.setScreen(null);stage=4;ticks=0;return;
            }
            if(stage==4&&ticks>=20){check(GpuDispatcher.enabled(),"GPU disabled after inventory");finish(mc,null);}
        }catch(Throwable error){finish(mc,error);}
    }
    private static long number(Map<?,?> map,String key){return map.get(key) instanceof Number n?n.longValue():0;}
    private static void check(boolean pass,String failure){if(!pass)failures.add(failure);}
    private static void screenshot(Minecraft mc,String name)throws Exception{try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(mc.gameDirectory.toPath().resolve(name+".png"));}}
    private static void finish(Minecraft mc,Throwable error){
        done=true;AnimationSamples.label="";
        try{
            if(error!=null){failures.add(error.toString());RenderOptimizer.LOGGER.error("DSRO startup smoke failed",error);}
            result.put("pass",failures.isEmpty());result.put("failures",failures);result.put("kind","startup-world-smoke");result.put("performanceResampled",false);
            result.put("compatibility",top.wu949.dsbr.optimizer.compat.OptimizerMixinPlugin.status);
            RenderOptimizer.clear();
            Files.writeString(mc.gameDirectory.toPath().resolve("smoke-result.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
        }catch(Exception failure){failure.printStackTrace();}
        mc.stop();
    }
}
