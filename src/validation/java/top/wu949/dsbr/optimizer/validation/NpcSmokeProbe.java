package top.wu949.dsbr.optimizer.validation;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.cache.object.*;
import top.wu949.dsbr.optimizer.*;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import top.wu949.dsbr.optimizer.gpu.GpuDispatcher;
import top.wu949.dsbr.optimizer.texture.TextureCache;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Isolated candidate-only visual evidence. Core is accessed reflectively in this test driver. */
@EventBusSubscriber(modid="dsbr_validation",value=Dist.CLIENT)
public final class NpcSmokeProbe {
    private static final List<Mob> npcs=new ArrayList<>();
    private static final List<Map<String,Object>> rows=new ArrayList<>();
    private static final Map<Integer,Set<Long>> hashes=new HashMap<>();
    private static final Map<Integer,Set<String>> animations=new HashMap<>();
    private static List<String> packs;
    private static boolean creating,done,view;
    private static int ticks,phase,pack=-1,action;
    private static CompletableFuture<Void> setup,reload;
    private static CompletableFuture<List<Integer>> spawning;
    private static CompletableFuture<Void> actionUpdate;
    private static final String[] actions={"idle","walk","run","fly","attack","dance"};
    public static boolean fixedCamera(){return view&&!done;}
    public static void capture(GeoAnimatable actor,BakedGeoModel model){
        if(!Boolean.getBoolean("beloongrender.npcSmoke")||!view||!(actor instanceof Mob mob)||!npcs.contains(mob))return;
        long hash=1;var todo=new ArrayDeque<>(model.topLevelBones());
        while(!todo.isEmpty()){
            var b=todo.removeFirst();todo.addAll(b.getChildBones());
            for(float f:new float[]{b.getRotX(),b.getRotY(),b.getRotZ(),b.getPosX(),b.getPosY(),b.getPosZ(),b.getScaleX(),b.getScaleY(),b.getScaleZ()}){
                check(Float.isFinite(f),"Non-finite NPC bone "+b.getName());hash=hash*31+Float.floatToIntBits(f);
            }
        }
        hashes.computeIfAbsent(mob.getId(),k->new HashSet<>()).add(hash);
        if(actor instanceof GeoEntity geo)geo.getAnimatableInstanceCache().getManagerForId(mob.getId()).getAnimationControllers().forEach((name,c)->{
            if(c.getCurrentAnimation()!=null)animations.computeIfAbsent(mob.getId(),k->new HashSet<>()).add(c.getCurrentAnimation().animation().name());
        });
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!Boolean.getBoolean("beloongrender.npcSmoke")||done)return;var mc=Minecraft.getInstance();
        try{
            check(mc.gameDirectory.getName().startsWith("dsbr-validation-"),"Own instance required");
            if(!creating){
                if(!(mc.screen instanceof TitleScreen))return;creating=true;GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(6);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);
                mc.createWorldOpenFlows().openWorld("multi-render-validation",()->{throw new IllegalStateException("Fixture failed");});return;
            }
            if(phase==9){if(++ticks>=20){check(GpuDispatcher.bytes()==0&&TextureCache.bytes()==0,"Logout resources retained");finish(mc,null);}return;}
            if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            if(setup==null){setup=SingleRoundProbe.setModel(mc,0);return;}if(!setup.isDone())return;setup.join();
            if(phase==0){
                if(++ticks<100)return;
                if(spawning==null){
                    spawning=CompletableFuture.supplyAsync(()->{
                        var level=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).serverLevel();var ids=new ArrayList<Integer>();
                        for(int i=0;i<4;i++){
                            var actor=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("beloong:"+(i%2==0?"mo":"dihuang_loong"))).create(level);
                            check(actor instanceof Mob,"NPC type absent");var mob=(Mob)actor;mob.setNoAi(true);mob.setNoGravity(true);
                            mob.getAttribute(Attributes.SCALE).setBaseValue(new double[]{.75,1.2,.9,1.1}[i]);mob.refreshDimensions();
                            mob.moveTo(i*6-9,-60,-4,0,0);mob.setOldPosAndRot();level.addFreshEntity(mob);ids.add(mob.getId());
                        }return ids;
                    },mc.getSingleplayerServer());return;
                }
                if(!spawning.isDone())return;var ids=spawning.join();if(ids.stream().anyMatch(id->!(mc.level.getEntity(id)instanceof Mob)))return;
                for(var id:ids)npcs.add((Mob)mc.level.getEntity(id));
                mc.setScreen(null);mc.options.hideGui=true;mc.options.setCameraType(CameraType.FIRST_PERSON);
                var camera=new ArmorStand(mc.level,0,-52,36);camera.moveTo(0,-52,36,180,13);camera.setOldPosAndRot();mc.setCameraEntity(camera);view=true;
                mc.player.setPos(13,-60,2);mc.player.getAbilities().flying=true;
                try(var files=Files.list(mc.gameDirectory.toPath().resolve("shaderpacks"))){packs=files.filter(p->p.toString().endsWith(".zip")).map(p->p.getFileName().toString()).sorted().toList();}
                check(packs.size()==8,"Eight shader packs required");
                if(Boolean.getBoolean("beloongrender.npcLifecycleOnly")){
                    pack=packs.indexOf("ComplementaryUnbound_r5.9.zip");check(pack>=0,"Unbound absent");loadShader();
                    OptimizerConfig.MODE.set(OptimizerConfig.Mode.GPU);OptimizerConfig.NPC_GPU.set(false);reset();phase=4;
                }else load(mc);return;
            }
            mc.player.setYRot(180);mc.player.yRotO=180;mc.player.setYHeadRot(180);mc.player.yHeadRotO=180;mc.player.setYBodyRot(180);mc.player.yBodyRotO=180;
            for(int i=0;i<npcs.size();i++){var mob=npcs.get(i);mob.setYHeadRot((float)Math.sin(mob.tickCount*.05+i)*25);mob.setXRot((float)Math.sin(mob.tickCount*.03+i)*10);}
            if(phase==3&&actionUpdate!=null){if(!actionUpdate.isDone())return;actionUpdate.join();}
            ticks++;
            if(phase==1&&ticks>=80){verifyShader();snapshot(mc,"pack-"+pack+"-cpu");OptimizerConfig.MODE.set(OptimizerConfig.Mode.GPU);reset();phase=2;}
            else if(phase==2&&ticks>=80){verifyShader();verifyGpu();snapshot(mc,"pack-"+pack+"-gpu");if(++pack<packs.size())load(mc);else{pack=packs.indexOf("ComplementaryUnbound_r5.9.zip");check(pack>=0,"Unbound absent");loadShader();OptimizerConfig.MODE.set(OptimizerConfig.Mode.GPU);phase=3;action=0;setAction();reset();}}
            else if(phase==3&&ticks>=60){
                verifyGpu();check(hashes.size()==4,"NPC not sampled");for(var mob:npcs){check(hashes.get(mob.getId()).size()>1,"Frozen NPC pose");check(animations.getOrDefault(mob.getId(),Set.of()).contains(actions[action]),"Action not evaluated: "+actions[action]);}
                snapshot(mc,"action-"+actions[action]);if(++action<actions.length){setAction();reset();}else{OptimizerConfig.NPC_GPU.set(false);reset();phase=4;}
            }
            else if(phase==4&&ticks>=40){check(total("NPC_CPU_BONE")>0&&total("NPC_GPU_PASS")==0&&total("PLAYER_GPU_PASS")>0,"NPC opt-out changed DS");snapshot(mc,"npc-disabled");OptimizerConfig.NPC_GPU.set(true);reload=mc.reloadResourcePacks();reset();phase=5;}
            else if(phase==5&&reload.isDone()&&ticks>=80){reload.join();verifyGpu();snapshot(mc,"after-reload");view=false;mc.setCameraEntity(mc.player);mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.options.hideGui=false;mc.gameMode.setLocalMode(GameType.SURVIVAL);mc.setScreen(new InventoryScreen(mc.player));reset();phase=6;}
            else if(phase==6&&ticks>=40){check(total("GUI_CPU_PASS")>0,"Inventory preview not CPU");snapshot(mc,"inventory");mc.setScreen(null);OptimizerConfig.MODE.set(OptimizerConfig.Mode.VANILLA);reset();phase=7;}
            else if(phase==7&&ticks>=20){OptimizerConfig.MODE.set(OptimizerConfig.Mode.GPU);view=true;reset();phase=8;}
            else if(phase==8&&ticks>=60){verifyGpu();snapshot(mc,"after-mode-switch");view=false;mc.level.disconnect();mc.disconnect(new TitleScreen());ticks=0;phase=9;}
        }catch(Throwable error){finish(mc,error);}
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static void setAction()throws Exception{
        var state=Class.forName("com.zonlong.beloong.entity.NpcState");var flying=Enum.valueOf((Class)state,"FLYING");var idle=Enum.valueOf((Class)state,"IDLE");
        var mc=Minecraft.getInstance();String selected=actions[action];var ids=npcs.stream().map(Entity::getId).toList();
        actionUpdate=CompletableFuture.runAsync(()->{
            var level=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).serverLevel();
            for(var id:ids){var npc=level.getEntity(id);check(npc!=null,"Server NPC missing");
                try{npc.getClass().getMethod("clearEmote").invoke(npc);npc.getClass().getMethod("setState",state).invoke(npc,selected.equals("fly")?flying:idle);
                    if(!selected.equals("idle")&&!selected.equals("fly"))npc.getClass().getMethod("setEmote",String.class).invoke(npc,selected);
                }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
            }
        },mc.getSingleplayerServer());
    }
    private static void load(Minecraft mc)throws Exception{OptimizerConfig.MODE.set(OptimizerConfig.Mode.TEXTURES);loadShader();reset();phase=1;}
    private static void loadShader()throws Exception{var iris=Class.forName("net.irisshaders.iris.Iris");var config=iris.getMethod("getIrisConfig").invoke(null);config.getClass().getMethod("setShaderPackName",String.class).invoke(config,pack<0?"ComplementaryUnbound_r5.9.zip":packs.get(pack));config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,pack>=0);config.getClass().getMethod("save").invoke(config);iris.getMethod("reload").invoke(null);}
    private static void verifyShader()throws Exception{var iris=Class.forName("net.irisshaders.iris.Iris");check((boolean)iris.getMethod("isPackInUseQuick").invoke(null)==(pack>=0),"Shader state incorrect");if(pack>=0)check(!(boolean)iris.getMethod("loadedIncompatiblePack").invoke(null)&&packs.get(pack).equals(iris.getMethod("getCurrentPackName").invoke(null)),"Shader failed");}
    private static void verifyGpu(){check(GpuDispatcher.enabled()&&total("NPC_GPU_PASS")>0&&total("PLAYER_GPU_PASS")>0,"GPU path failed");check(total("READBACK")==0,"Normal rendering CPU readback");check(((Map<?,?>)Diagnostics.INSTANCE.snapshot().get("fallbacks")).isEmpty(),"Unexpected fallback");}
    private static long total(String key){var t=(Map<?,?>)Diagnostics.INSTANCE.snapshot().get("totals");return t.get(key)instanceof Number n?n.longValue():0;}
    private static void reset(){ticks=0;Diagnostics.INSTANCE.reset();hashes.clear();animations.clear();}
    private static void snapshot(Minecraft mc,String name)throws Exception{try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(mc.gameDirectory.toPath().resolve(name+".png"));}rows.add(Map.of("case",name,"shader",pack<0?"disabled":packs.get(pack),"stats",Diagnostics.INSTANCE.snapshot(),"poseVariants",hashes.entrySet().stream().collect(java.util.stream.Collectors.toMap(e->e.getKey().toString(),e->e.getValue().size())),"animations",animations.toString()));save(mc);}
    private static void save(Minecraft mc)throws Exception{Files.writeString(mc.gameDirectory.toPath().resolve("npc-smoke-scenes.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(rows));}
    private static void finish(Minecraft mc,Throwable error){done=true;try{if(error!=null)RenderOptimizer.LOGGER.error("NPC smoke failed",error);RenderOptimizer.clear();save(mc);var result=new LinkedHashMap<String,Object>();result.put("pass",error==null&&GpuDispatcher.bytes()==0&&TextureCache.bytes()==0);result.put("error",error==null?null:error.toString());result.put("cases",rows.size());result.put("meshBytesAfterClear",GpuDispatcher.bytes());result.put("textureBytesAfterClear",TextureCache.bytes());result.put("compatibility",top.wu949.dsbr.optimizer.compat.OptimizerMixinPlugin.status);Files.writeString(mc.gameDirectory.toPath().resolve("npc-smoke-result.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception failure){failure.printStackTrace();}mc.stop();}
    private static void check(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
}
