package top.wu949.dsbr.optimizer.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import top.wu949.dsbr.optimizer.RenderOptimizer;
import top.wu949.dsbr.optimizer.gpu.GpuDispatcher;
import top.wu949.dsbr.optimizer.texture.TextureCache;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.List;

/** Bounded alpha.4 comparison for the Iris sky warning, separate from performance data. */
@EventBusSubscriber(modid="dsbr_validation",value=Dist.CLIENT)
public final class ReloadTextureProbe {
    private static boolean creating,done;
    private static int phase,ticks;
    private static CompletableFuture<Void> setup,reload;
    private static CompletableFuture<List<Integer>> spawning;
    private static boolean view;
    public static boolean fixedCamera(){return view&&!done;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("beloongrender.reloadDiagnostic")||done)return;
        var mc=Minecraft.getInstance();
        try {
            if(!mc.gameDirectory.getName().startsWith("dsbr-validation-"))throw new IllegalStateException("Own instance required");
            if(!creating){if(!(mc.screen instanceof TitleScreen))return;creating=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().openWorld("multi-render-validation",()->{});return;}
            if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
            if(setup==null){setup=SingleRoundProbe.setModel(mc,0);return;}if(!setup.isDone())return;setup.join();ticks++;
            if(phase==0&&ticks>=60){
                if(spawning==null){spawning=CompletableFuture.supplyAsync(()->{var level=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).serverLevel();var ids=new java.util.ArrayList<Integer>();for(int i=0;i<4;i++){var actor=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.parse("beloong:"+(i%2==0?"mo":"dihuang_loong"))).create(level);var mob=(net.minecraft.world.entity.Mob)actor;mob.setNoAi(true);mob.setNoGravity(true);mob.moveTo(i*6-9,-60,-4,0,0);level.addFreshEntity(mob);ids.add(mob.getId());}return ids;},mc.getSingleplayerServer());return;}
                if(!spawning.isDone())return;var ids=spawning.join();if(ids.stream().anyMatch(id->mc.level.getEntity(id)==null))return;
                var iris=Class.forName("net.irisshaders.iris.Iris");if(!(boolean)iris.getMethod("isPackInUseQuick").invoke(null)||!"ComplementaryUnbound_r5.9.zip".equals(iris.getMethod("getCurrentPackName").invoke(null)))throw new IllegalStateException("Unbound must be active");
                mc.setScreen(null);mc.options.hideGui=true;mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                var camera=new net.minecraft.world.entity.decoration.ArmorStand(mc.level,0,-52,36);camera.moveTo(0,-52,36,180,13);camera.setOldPosAndRot();mc.setCameraEntity(camera);view=true;ticks=0;phase=1;
            }
            else if(phase==1&&ticks>=60){try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(mc.gameDirectory.toPath().resolve("reload-diagnostic-before.png"));}reload=mc.reloadResourcePacks();ticks=0;phase=2;}
            else if(phase==2&&reload.isDone()&&ticks>=80){reload.join();RenderOptimizer.clear();Files.writeString(mc.gameDirectory.toPath().resolve("reload-diagnostic-result.json"),new com.google.gson.Gson().toJson(Map.of("pass",GpuDispatcher.enabled()&&GpuDispatcher.bytes()==0&&TextureCache.bytes()==0,"compatibility",top.wu949.dsbr.optimizer.compat.OptimizerMixinPlugin.status)));done=true;mc.stop();}
        }catch(Exception failure){RenderOptimizer.LOGGER.error("Reload diagnostic failed",failure);done=true;mc.stop();}
    }
}
