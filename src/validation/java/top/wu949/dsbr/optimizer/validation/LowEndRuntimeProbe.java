package top.wu949.dsbr.optimizer.validation;

import by.dragonsurvivalteam.dragonsurvival.common.capability.*;
import by.dragonsurvivalteam.dragonsurvival.common.entity.DragonEntity;
import by.dragonsurvivalteam.dragonsurvival.client.render.entity.dragon.DragonRenderer;
import by.dragonsurvivalteam.dragonsurvival.registry.attachments.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import top.wu949.dsbr.optimizer.*;
import top.wu949.dsbr.optimizer.lowend.*;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import top.wu949.dsbr.optimizer.gpu.GpuDispatcher;
import top.wu949.dsbr.optimizer.texture.TextureCache;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid="dsbr_validation",value=Dist.CLIENT)
public final class LowEndRuntimeProbe {
    private static final boolean ACTIVE=Boolean.getBoolean("beloongrender.lowEndRuntime");
    private static final boolean PREVIEW_ONLY=Boolean.getBoolean("beloongrender.lowEndPreviewOnly");
    private static final boolean RELOAD_ONLY=Boolean.getBoolean("beloongrender.lowEndReloadOnly");
    private static final boolean LIFECYCLE=Boolean.getBoolean("beloongrender.lowEndLifecycleOnly");
    private static boolean breathControl;
    private static String boneDebug="not captured";
    private static final boolean TARGETED=Boolean.getBoolean("beloongrender.lowEndTargeted");
    private static boolean creating,done,view,far;
    private static int stage,ticks,model,action;
    private static CompletableFuture<Void> setup,reload;
    private static CompletableFuture<List<Integer>> spawning;
    private static final List<Mob> npcs=new ArrayList<>();
    private static final List<Map<String,Object>> rows=new ArrayList<>();
    private static final Map<String,Set<Long>> hashes=new HashMap<>();
    private static final Map<String,Set<String>> animations=new HashMap<>();
    private static long firstTick;
    private static String savedSkin;
    private static final String[] species={"cave_dragon","tundra_dragon_human","aether_dragon_human"};
    public static boolean fixedCamera(){return ACTIVE&&view&&!done;}
    public static boolean farCamera(){return far;}
    public static void capture(GeoAnimatable actor,BakedGeoModel baked){
        if(!ACTIVE||done)return;
        String id=actor instanceof DragonEntity d&&d.getPlayer()==Minecraft.getInstance().player?"player":actor instanceof Mob m&&npcs.contains(m)?"npc-"+m.getId():null;
        if(id==null)return;
        if(id.equals("player"))boneDebug="neck="+baked.getBone("Neck").map(b->"hidden="+b.isHidden()+"/children="+b.isHidingChildren()).orElse("missing")+", model="+DragonStateProvider.getData(Minecraft.getInstance().player).getModel()+", bone="+baked.getBone(DragonRenderer.BREATH_SOURCE).map(b->b.getName()+"/tracking="+b.isTrackingMatrices()+"/hidden="+b.isHidden()).orElse("missing")+", positions="+DragonRenderer.BONE_POSITIONS;
        long hash=1;var todo=new ArrayDeque<>(baked.topLevelBones());
        while(!todo.isEmpty()){
            var bone=todo.removeFirst();todo.addAll(bone.getChildBones());
            for(float f:new float[]{bone.getRotX(),bone.getRotY(),bone.getRotZ(),bone.getPosX(),bone.getPosY(),bone.getPosZ(),bone.getScaleX(),bone.getScaleY(),bone.getScaleZ()}){
                check(Float.isFinite(f),"Non-finite bone "+id+"/"+bone.getName());hash=31*hash+Float.floatToIntBits(f);
            }
        }
        hashes.computeIfAbsent(id,k->new HashSet<>()).add(hash);
        long instance=actor instanceof Entity e?e.getId():0;
        actor.getAnimatableInstanceCache().getManagerForId(instance).getAnimationControllers().forEach((name,c)->{
            if(c.getCurrentAnimation()!=null)animations.computeIfAbsent(id,k->new HashSet<>()).add(c.getCurrentAnimation().animation().name());
        });
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post ignored){
        if(!ACTIVE||done)return;var mc=Minecraft.getInstance();
        try{
            if(!mc.gameDirectory.getName().startsWith("dsbr-validation-"))throw new IllegalStateException("Isolated directory required");
            if(!creating){
                if(!(mc.screen instanceof TitleScreen))return;
                check(ModList.get().getModContainerById("dsbr").orElseThrow().getModInfo().getVersion().toString().equals("0.2.1"),"Wrong release version");
                screenshot(mc,"title");creating=true;mc.options.pauseOnLostFocus=false;mc.options.enableVsync().set(false);mc.options.renderDistance().set(6);mc.options.framerateLimit().set(260);
                mc.createWorldOpenFlows().createFreshLevel("multi-render-validation",new LevelSettings("DSRO 0.2.1 isolated checks",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(2067,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());return;
            }
            if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;ticks++;
            if(stage==0){
                if(setup==null){setup=SingleRoundProbe.setModel(mc,0);ticks=0;return;}
                if(!setup.isDone()||ticks<100)return;setup.join();
                if(spawning==null){spawning=CompletableFuture.supplyAsync(()->{
                    var level=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).serverLevel();var ids=new ArrayList<Integer>();
                    for(int i=0;i<2;i++){var mob=(Mob)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("beloong:"+(i==0?"mo":"dihuang_loong"))).create(level);mob.setNoAi(true);mob.setNoGravity(true);mob.moveTo(i*7-4,-60,4,0,0);mob.setOldPosAndRot();level.addFreshEntity(mob);ids.add(mob.getId());}return ids;
                },mc.getSingleplayerServer());return;}
                if(!spawning.isDone())return;var ids=spawning.join();if(ids.stream().anyMatch(id->!(mc.level.getEntity(id) instanceof Mob)))return;for(int id:ids)npcs.add((Mob)mc.level.getEntity(id));
                check(!OptimizerConfig.LOW_END.get(),"Fresh default must be disabled");
                OptimizerConfig.LOW_END.set(true);OptimizerConfig.SPEC.save();
                check(Files.readString(mc.gameDirectory.toPath().resolve("config/dsbr-optimizer-client.toml")).contains("lowEndGpuSupport = true"),"Config did not save");
                mc.setScreen(new ConfigurationScreen(ModList.get().getModContainerById("dsbr").orElseThrow(),null));stage=1;ticks=0;return;
            }
            if(stage==1&&ticks>=15){
                screenshot(mc,"configuration");check(OptimizerConfig.textureMiB()==16&&OptimizerConfig.meshMiB()==64&&OptimizerConfig.retentionSeconds()==10,"Effective budget mismatch");
                view=true;mc.setScreen(null);mc.options.hideGui=true;mc.options.setCameraType(CameraType.FIRST_PERSON);var camera=new ArmorStand(mc.level,0,-55,20);camera.moveTo(0,-55,20,180,12);camera.setOldPosAndRot();mc.setCameraEntity(camera);
                SingleRoundProbe.souls(mc,4,0,"idle",false);setup=null;stage=2;ticks=0;return;
            }
            if(view){mc.player.setYRot(180);mc.player.yRotO=180;mc.player.setYHeadRot(180);mc.player.yHeadRotO=180;mc.player.setYBodyRot(180);mc.player.yBodyRotO=180;}
            if(stage==2){
                if(setup==null){setup=SingleRoundProbe.setModel(mc,model);ticks=0;return;}
                if(!setup.isDone()||ticks<60||!DragonStateProvider.getData(mc.player).speciesId().getPath().equals(species[model]))return;setup.join();
                savedSkin=DragonStateProvider.getData(mc.player).getSkinData().serializeNBT(mc.player.registryAccess()).toString();if(PREVIEW_ONLY){far=true;stage=6;ticks=0;return;}if(RELOAD_ONLY){reload=mc.reloadResourcePacks();reset(mc);stage=10;return;}if(LIFECYCLE){stage=4;setup=null;ticks=0;return;}action=TARGETED?1:0;setAction(mc);stage=3;ticks=0;return;
            }
            if(stage==3){
                movement(mc);if(ticks==20)resetSamples(mc);
                if(ticks<80)return;
                check(hashes.containsKey("player")&&npcs.stream().allMatch(n->hashes.containsKey("npc-"+n.getId())),"Missing visible actor");
                for(var entry:hashes.entrySet())check(entry.getValue().size()>1,"Frozen nearby pose "+entry.getKey());
                check(total("LOW_END_ANIMATION_UPDATE")>0&&total("LOW_END_ANIMATION_REUSED")>total("LOW_END_ANIMATION_UPDATE"),"Animation throttle inactive");
                check(total("LOW_END_ANIMATION_UPDATE")<=((mc.level.getGameTime()-firstTick)/4+2)*3,"Animation update rate exceeded 5 Hz");
                check(total("LOW_END_SKIN_BASE")>0&&total("LOW_END_SOUL_SKIPPED")>0&&total("SOUL_GPU_PASS")==0,"Low-end skin/soul cuts inactive");
                check(savedSkin.equals(DragonStateProvider.getData(mc.player).getSkinData().serializeNBT(mc.player.registryAccess()).toString()),"Skin data changed");
                String current=animations.getOrDefault("player",Set.of()).toString();check(action!=1||current.contains("walk"),"Player walk controller absent: "+current);check(action!=3||current.contains("bite")||current.contains("use_item"),"Player attack controller absent: "+current);snapshot(mc,"model-"+model+"-"+new String[]{"idle","walk","fly","attack"}[action]);
                action+=TARGETED?2:1;if(action<4){setAction(mc);ticks=0;return;}
                if(++model<3){setup=null;stage=2;return;}model=0;setup=null;stage=4;ticks=0;return;
            }
            if(stage==4){
                if(setup==null){setup=SingleRoundProbe.setModel(mc,0);ticks=0;return;}if(!setup.isDone()||ticks<60)return;setup.join();
                view=false;mc.setCameraEntity(mc.player);mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.player.input.leftImpulse=0;MovementData.getData(mc.player).bite=false;
                var magic=MagicData.getData(mc.player);var ability=magic.fromSlot(0);check(ability!=null,"Missing breath ability");SingleRoundProbe.mana(mc.player);ability.setCooldown(0);
                mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());SingleRoundProbe.mana(p);var a=MagicData.getData(p).fromSlot(0);if(a!=null)a.setCooldown(0);});
                check(magic.attemptCast(mc.player,0),"Breath cast rejected");PacketDistributor.sendToServer(new by.dragonsurvivalteam.dragonsurvival.network.magic.SyncBeginCast(mc.player.getId(),0));stage=5;ticks=0;return;
            }
            if(stage==5){
                if(ticks==30){check(MagicData.getData(mc.player).isCasting(),"Breath stopped early");var position=DragonRenderer.getBonePositionOrNull(mc.player,DragonRenderer.BREATH_SOURCE);if(position==null&&!breathControl){breathControl=true;OptimizerConfig.LOW_END.set(false);OptimizerConfig.MODE.set(OptimizerConfig.Mode.VANILLA);stage=11;ticks=0;return;}check(position!=null&&Double.isFinite(position.x)&&Double.isFinite(position.y)&&Double.isFinite(position.z)&&position.distanceTo(mc.player.position())<20,"Invalid breath locator: position="+position+" player="+mc.player.position()+" fresh="+DragonRenderer.isBonePositionFresh(mc.player,DragonRenderer.BREATH_SOURCE)+" "+boneDebug);snapshot(mc,"breath");}
                if(ticks==50){MagicData.getData(mc.player).stopCasting(mc.player);PacketDistributor.sendToServer(new by.dragonsurvivalteam.dragonsurvival.network.magic.SyncStopCast(mc.player.getId(),Optional.empty()));}
                if(ticks<80)return;check(!MagicData.getData(mc.player).isCasting(),"Breath did not stop");snapshot(mc,"breath-recovery");view=true;far=true;mc.options.setCameraType(CameraType.FIRST_PERSON);var farView=new ArmorStand(mc.level,0,-55,60);farView.moveTo(0,-55,60,180,12);farView.setOldPosAndRot();mc.setCameraEntity(farView);stage=6;ticks=0;return;
            }
            if(stage==11&&ticks>=30){var pos=DragonRenderer.getBonePositionOrNull(mc.player,DragonRenderer.BREATH_SOURCE);check(pos!=null,"Baseline breath locator missing: "+boneDebug);OptimizerConfig.MODE.set(OptimizerConfig.Mode.GPU);OptimizerConfig.LOW_END.set(true);stage=5;ticks=20;return;}
            if(stage==6){if(ticks==20)resetSamples(mc);if(ticks<80)return;for(var npc:npcs)check(hashes.getOrDefault("npc-"+npc.getId(),Set.of()).size()==1,"Far NPC not held: id="+npc.getId()+", poses="+hashes+", alive="+npc.isAlive()+", camera="+mc.gameRenderer.getMainCamera().getPosition()+", mode="+OptimizerConfig.MODE.get()+", low="+OptimizerConfig.LOW_END.get());snapshot(mc,"far-static");far=false;view=false;mc.setCameraEntity(mc.player);mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.options.hideGui=false;mc.gameMode.setLocalMode(GameType.SURVIVAL);mc.setScreen(new InventoryScreen(mc.player));reset(mc);stage=7;return;}
            if(stage==7&&ticks>=60){check(mc.screen instanceof InventoryScreen&&total("GUI_CPU_PASS")>0&&total("LOW_END_SKIN_BASE")>0,"Inventory preview failed");check(!boneDebug.contains("neck=hidden=true"),"Inventory neck hidden: "+boneDebug);snapshot(mc,"inventory");mc.setScreen(null);OptimizerConfig.MODE.set(OptimizerConfig.Mode.TEXTURES);reset(mc);stage=8;return;}
            if(stage==8&&ticks>=60){check(!GpuDispatcher.enabled()&&total("CPU_VERTEX_SUBMIT_NANOS")>0&&total("LOW_END_ANIMATION_REUSED")>0&&total("LOW_END_SKIN_BASE")>0,"CPU fallback cuts failed");snapshot(mc,"cpu-fallback");OptimizerConfig.MODE.set(OptimizerConfig.Mode.GPU);OptimizerConfig.LOW_END.set(false);reset(mc);stage=9;return;}
            if(stage==9&&ticks>=60){check(total("LOW_END_SKIN_BASE")==0&&total("LOW_END_ANIMATION_UPDATE")==0&&total("SOUL_GPU_PASS")>0&&GpuDispatcher.enabled(),"Disabled mode did not restore");check(hashes.getOrDefault("player",Set.of()).size()>1,"Restored animation frozen");snapshot(mc,"restored");OptimizerConfig.LOW_END.set(true);reload=mc.reloadResourcePacks();reset(mc);stage=10;return;}
            if(stage==10&&reload.isDone()&&ticks>=80){reload.join();check(total("LOW_END_SKIN_BASE")>0&&LowEndAnimations.bytes()>0,"Reload did not restore low-end resources");snapshot(mc,"reload");OptimizerConfig.LOW_END.set(false);OptimizerConfig.SPEC.save();finish(mc,null);}
        }catch(Throwable error){finish(mc,error);}
    }
    private static void movement(Minecraft mc){mc.player.fallDistance=0;mc.player.getAbilities().flying=action==2;mc.player.input.leftImpulse=action==1?1:0;mc.player.input.forwardImpulse=0;var move=MovementData.getData(mc.player);move.bite=action==3;move.dig=false;var velocity=new Vec3(action==1?.3:0,0,0);move.set(0,0,0,velocity);move.setDesiredMoveVec(action==1?new Vec3(1,0,0):Vec3.ZERO);if(action==3&&ticks%15==0)mc.player.swing(InteractionHand.MAIN_HAND);}
    @SuppressWarnings({"unchecked","rawtypes"})private static void setAction(Minecraft mc)throws Exception{var type=Class.forName("com.zonlong.beloong.entity.NpcState");var state=Enum.valueOf((Class)type,action==2?"FLYING":"IDLE");var ids=npcs.stream().map(Entity::getId).toList();int selected=action;mc.getSingleplayerServer().execute(()->{for(int id:ids){var npc=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).serverLevel().getEntity(id);try{npc.getClass().getMethod("clearEmote").invoke(npc);npc.getClass().getMethod("setState",type).invoke(npc,state);if(selected==1||selected==3)npc.getClass().getMethod("setEmote",String.class).invoke(npc,selected==1?"walk":"attack");}catch(Exception e){throw new IllegalStateException(e);}}});}
    private static void reset(Minecraft mc){ticks=0;resetSamples(mc);}
    private static void resetSamples(Minecraft mc){hashes.clear();animations.clear();Diagnostics.INSTANCE.reset();firstTick=mc.level.getGameTime();}
    private static long total(String key){var t=(Map<?,?>)Diagnostics.INSTANCE.snapshot().get("totals");return t.get(key)instanceof Number n?n.longValue():0;}
    private static void screenshot(Minecraft mc,String name)throws Exception{try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(mc.gameDirectory.toPath().resolve("lowend-"+name+".png"));}}
    private static void snapshot(Minecraft mc,String name)throws Exception{screenshot(mc,name);rows.add(Map.of("case",name,"stats",Diagnostics.INSTANCE.snapshot(),"poses",hashes.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().size())),"animations",animations.toString(),"poseBytes",LowEndAnimations.bytes()));save(mc);}
    private static void save(Minecraft mc)throws Exception{Files.writeString(mc.gameDirectory.toPath().resolve("lowend-runtime-scenes.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(rows));}
    private static void finish(Minecraft mc,Throwable error){done=true;try{if(error!=null)RenderOptimizer.LOGGER.error("Low-end runtime check failed",error);RenderOptimizer.clear();save(mc);var result=new LinkedHashMap<String,Object>();result.put("pass",error==null&&TextureCache.bytes()==0&&GpuDispatcher.bytes()==0&&LowEndAnimations.bytes()==0);result.put("error",error==null?null:error.toString());result.put("cases",rows.size());result.put("textureBytesAfterClear",TextureCache.bytes());result.put("meshBytesAfterClear",GpuDispatcher.bytes());result.put("animationBytesAfterClear",LowEndAnimations.bytes());result.put("compatibility",top.wu949.dsbr.optimizer.compat.OptimizerMixinPlugin.status);Files.writeString(mc.gameDirectory.toPath().resolve("lowend-runtime-result.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception e){e.printStackTrace();}mc.stop();}
    private static void check(boolean valid,String reason){if(!valid)throw new IllegalStateException(reason);}
}
