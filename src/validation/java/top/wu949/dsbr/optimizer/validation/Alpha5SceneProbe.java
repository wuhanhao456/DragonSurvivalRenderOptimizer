package top.wu949.dsbr.optimizer.validation;

import com.mojang.authlib.GameProfile;
import by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateProvider;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import top.wu949.dsbr.optimizer.*;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import top.wu949.dsbr.optimizer.gpu.GpuDispatcher;
import top.wu949.dsbr.optimizer.texture.TextureCache;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Same driver for baseline and candidate; no references to new optimizer or optional mod types. */
@EventBusSubscriber(modid = "dsbr_validation", value = Dist.CLIENT)
public final class Alpha5SceneProbe {
    private static final String[] SCENES = System.getProperty("beloongrender.alpha5Scenes", "").split(",");
    private static final long WARM = Long.getLong("beloongrender.warm",15L)*1_000_000_000L, SAMPLE=Long.getLong("beloongrender.sample",60L)*1_000_000_000L;
    private static final List<Entity> actors = new ArrayList<>();
    private static final List<Map<String,Object>> rows = new ArrayList<>();
    private static boolean creating, done, sampling;
    private static int scene, ticks;
    private static long deadline;
    private static CompletableFuture<Void> setup;
    private static ArmorStand camera;
    public static boolean fixedCamera() { return camera != null && !done; }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (System.getProperty("beloongrender.alpha5Scenes", "").isEmpty() || done) return;
        var mc = Minecraft.getInstance();
        try {
            if (!mc.gameDirectory.getName().startsWith("dsbr-validation-")) throw new IllegalStateException("Isolated test directory required");
            if (!creating) {
                if (!(mc.screen instanceof TitleScreen)) return;
                GLFW.glfwHideWindow(mc.getWindow().getWindow()); mc.options.pauseOnLostFocus=false;
                mc.options.enableVsync().set(false); mc.options.framerateLimit().set(260); mc.options.renderDistance().set(6); mc.options.hideGui=true;
                creating=true;
                if (Boolean.getBoolean("beloongrender.fixtureWorld")) mc.createWorldOpenFlows().openWorld("multi-render-validation", () -> { throw new IllegalStateException("Fixture failed"); });
                else mc.createWorldOpenFlows().createFreshLevel("multi-render-validation",new LevelSettings("alpha5 isolated short scenes",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new WorldOptions(2067,false,false), r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());
                return;
            }
            if (mc.player==null || mc.level==null || mc.getSingleplayerServer()==null) return;
            if (mc.screen!=null) mc.setScreen(null);
            if (setup==null) { setup=SingleRoundProbe.setModel(mc,0); return; }
            if (!setup.isDone()) return; setup.join();
            if (!DragonStateProvider.getData(mc.player).isDragon() || ++ticks<100) return;
            if (camera==null) {
                camera=new ArmorStand(mc.level,0,-52,36); camera.moveTo(0,-52,36,180,13); camera.setOldPosAndRot();
                mc.setCameraEntity(camera); mc.options.setCameraType(CameraType.FIRST_PERSON);
                OptimizerConfig.MODE.set(OptimizerConfig.Mode.GPU);
                feature("FRAME_ANIMATIONS",Boolean.getBoolean("beloongrender.frameAnimations"));
                spawn(mc); begin();
            }
            // DragonSurvival's camera hook uses the actual player's yaw.
            mc.player.setYRot(180); mc.player.yRotO=180; mc.player.setYHeadRot(180); mc.player.yHeadRotO=180;
            mc.player.setYBodyRot(180); mc.player.yBodyRotO=180;
            mc.player.getAbilities().flying=true; mc.player.fallDistance=0; mc.player.setDeltaMovement(0,0,0);
            if (System.nanoTime()<deadline) return;
            if (!sampling) {
                screenshot(mc,SCENES[scene]); Diagnostics.INSTANCE.reset(); NpcGeometryMeasurements.reset(); ProbeFrames.begin(); sampling=true; deadline=System.nanoTime()+SAMPLE; return;
            }
            ProbeFrames.save(mc.gameDirectory.toPath().resolve(SCENES[scene]+".csv"));
            var stats=Diagnostics.INSTANCE.snapshot(); var totals=(Map<?,?>)stats.get("totals");
            if (!GpuDispatcher.enabled() || number(totals,"GPU_PASS")==0) throw new IllegalStateException("No GPU drawing in scene "+SCENES[scene]);
            if (number(totals,"READBACK")>0) throw new IllegalStateException("Normal rendering readback");
            var row=new LinkedHashMap<String,Object>(); row.put("scene",SCENES[scene]); row.put("warmupSeconds",WARM/1e9); row.put("samplingSeconds",SAMPLE/1e9);
            row.put("width",mc.getWindow().getWidth());row.put("height",mc.getWindow().getHeight());row.put("renderDistance",mc.options.renderDistance().get());
            row.put("vsync",mc.options.enableVsync().get());row.put("effectiveFpsLimit",((top.wu949.dsbr.optimizer.validation.mixin.MinecraftFrameLimitAccess)(Object)mc).dsbr$getEffectiveFramerateLimit());
            row.put("swapInterval",org.lwjgl.opengl.WGLEXTSwapControl.wglGetSwapIntervalEXT());row.put("stats",stats);row.put("rawFrames",SCENES[scene]+".csv");
            row.put("actors",actors.stream().map(a->Map.of("type",BuiltInRegistries.ENTITY_TYPE.getKey(a.getType()).toString(),"scale",a instanceof LivingEntity l?l.getScale():1)).toList());
            row.put("textureBytes",TextureCache.bytes());row.put("meshBytes",GpuDispatcher.bytes());
            row.put("npcGeometryCalls",NpcGeometryMeasurements.calls);row.put("npcGeometryNanos",NpcGeometryMeasurements.nanos);
            var view=mc.gameRenderer.getMainCamera();row.put("camera",List.of(view.getPosition().x,view.getPosition().y,view.getPosition().z,view.getYRot(),view.getXRot()));
            if (Boolean.getBoolean("beloongrender.shaders")) {
                var iris=Class.forName("net.irisshaders.iris.Iris");
                if (!(boolean)iris.getMethod("isPackInUseQuick").invoke(null)) throw new IllegalStateException("Shaders inactive");
                row.put("shaderPack",iris.getMethod("getCurrentPackName").invoke(null));
            }
            rows.add(row); save(mc);
            if (++scene==SCENES.length) { finish(mc,null); return; }
            spawn(mc); begin();
        } catch(Throwable error) { finish(mc,error); }
    }
    private static void feature(String name,boolean value)throws Exception {
        try { ((net.neoforged.neoforge.common.ModConfigSpec.BooleanValue)OptimizerConfig.class.getField(name).get(null)).set(value); }
        catch(NoSuchFieldException baseline) { }
    }
    private static void spawn(Minecraft mc) {
        for(var e:actors) if(e!=mc.player)mc.level.removeEntity(e.getId(),Entity.RemovalReason.DISCARDED); actors.clear();
        SingleRoundProbe.souls(mc,0,0,"idle",false);
        String name=SCENES[scene]; int players=name.startsWith("players-")?Integer.parseInt(name.substring(8)):0;
        if(players>0) {
            actors.add(mc.player); var data=DragonStateProvider.getData(mc.player).serializeNBT(mc.player.registryAccess());
            for(int i=1;i<players;i++) {
                var p=new RemotePlayer(mc.level,new GameProfile(UUID.nameUUIDFromBytes(("isolated-alpha5-"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8)),"ProbeDragon"+i));
                DragonStateProvider.getData(p).deserializeNBT(p.registryAccess(),data.copy());p.getAttribute(Attributes.SCALE).setBaseValue(mc.player.getScale());p.refreshDimensions();
                actors.add(p);mc.level.addEntity(p);
            }
            for(int i=0;i<players;i++){var e=actors.get(i);e.moveTo((i%4)*5-7.5,-60,(i/4)*5-5,0,0);e.setOldPosAndRot();e.setNoGravity(true);}
        } else {
            // Keep the real DS player visible as a compatibility control.
            mc.player.setPos(13,-60,2);
            int amount=name.equals("mixed-12")?12:4;
            for(int i=0;i<amount;i++) {
                String type=name.equals("dihuang-4") || name.equals("mixed-12")&&i%2==1?"dihuang_loong":"mo";
                var entity=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("beloong",type)).create(mc.level);
                if(!(entity instanceof Mob mob))throw new IllegalStateException("Missing NPC type: "+type);
                mob.setNoAi(true);mob.setNoGravity(true);mob.moveTo((i%4)*6-9,-60,(i/4)*6-8,0,0);mob.setOldPosAndRot();
                actors.add(mob);mc.level.addEntity(mob);
            }
            if(name.equals("souls17-mo4")) SingleRoundProbe.souls(mc,17,0,"idle",false);
        }
    }
    private static long number(Map<?,?> map,String key){return map.get(key)instanceof Number n?n.longValue():0;}
    private static void begin(){sampling=false;deadline=System.nanoTime()+WARM;}
    private static void screenshot(Minecraft mc,String name)throws Exception {try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(mc.gameDirectory.toPath().resolve(name+".png"));}}
    private static void save(Minecraft mc)throws Exception {Files.writeString(mc.gameDirectory.toPath().resolve("alpha5-scenes.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(rows));}
    private static void finish(Minecraft mc,Throwable error) {
        done=true;
        try {
            if(error!=null)RenderOptimizer.LOGGER.error("Alpha5 isolated scenes failed",error);
            var stats=Diagnostics.INSTANCE.snapshot();RenderOptimizer.clear();save(mc);
            var result=new LinkedHashMap<String,Object>();result.put("pass",error==null&&GpuDispatcher.bytes()==0&&TextureCache.bytes()==0);result.put("error",error==null?null:error.toString());
            result.put("scenes",rows.size());result.put("stats",stats);result.put("meshBytesAfterClear",GpuDispatcher.bytes());result.put("textureBytesAfterClear",TextureCache.bytes());
            result.put("compatibility",top.wu949.dsbr.optimizer.compat.OptimizerMixinPlugin.status);
            Files.writeString(mc.gameDirectory.toPath().resolve("alpha5-result.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
        }catch(Exception failure){failure.printStackTrace();}mc.stop();
    }
}
