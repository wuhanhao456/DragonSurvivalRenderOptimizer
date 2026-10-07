package top.wu949.dsbr.optimizer.validation;

import by.dragonsurvivalteam.dragonsurvival.client.render.ClientDragonRenderer;
import by.dragonsurvivalteam.dragonsurvival.client.render.entity.dragon.DragonRenderer;
import by.dragonsurvivalteam.dragonsurvival.client.util.FakeClientPlayerUtils;
import by.dragonsurvivalteam.dragonsurvival.common.capability.*;
import by.dragonsurvivalteam.dragonsurvival.network.syncing.SyncComplete;
import by.dragonsurvivalteam.dragonsurvival.registry.*;
import by.dragonsurvivalteam.dragonsurvival.registry.attachments.*;
import by.dragonsurvivalteam.dragonsurvival.registry.data_components.*;
import by.dragonsurvivalteam.dragonsurvival.registry.dragon.DragonSpecies;
import by.dragonsurvivalteam.dragonsurvival.registry.dragon.body.DragonBody;
import by.dragonsurvivalteam.dragonsurvival.server.tileentity.DragonSoulBlockEntity;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
import org.lwjgl.glfw.GLFW;
import top.wu949.dsbr.optimizer.*;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
import top.wu949.dsbr.optimizer.gpu.GpuDispatcher;
import top.wu949.dsbr.optimizer.texture.TextureCache;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** One candidate launch: configuration, 1/4/12 souls, shaders and continuous animations. */
@EventBusSubscriber(modid = "dsbr_validation", value = Dist.CLIENT)
public final class SingleRoundProbe {
    private static final String[][] MODELS = {{"cave_dragon","east"},{"tundra_dragon_human","tundra_body_human"},{"aether_dragon_human","aether_body_human"}};
    private static final String[] ACTIONS = {"idle","walk","fly"};
    private static final int[] COUNTS = {1,4,12};
    private static final List<Map<String,Object>> rows = new ArrayList<>();
    private static final List<String> failures = new ArrayList<>();
    private static final List<BlockPos> positions = new ArrayList<>();
    private static final long START = System.nanoTime();
    private static boolean creating, done, sampling;
    private static int stage, ticks, count, shader, model, action;
    private static long deadline;
    private static CompletableFuture<Void> setup;
    private static ArmorStand camera;
    private static CompletableFuture<Void> reload;
    private static Path directory;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("beloongrender.singleRound") || done) return;
        var mc = Minecraft.getInstance();
        try {
            if (!mc.gameDirectory.getName().startsWith("dsbr-validation-")) throw new IllegalStateException("Isolated directory required");
            directory = mc.gameDirectory.toPath();
            if (System.nanoTime() - START > 900_000_000_000L) throw new IllegalStateException("Single-round deadline exceeded");
            if (!creating) {
                if (!(mc.screen instanceof TitleScreen)) return;
                if (OptimizerConfig.MODE.get() != OptimizerConfig.Mode.GPU) throw new IllegalStateException("Fresh config is not GPU");
                if (!Set.of(6,8,9).contains(OptimizerConfig.SPEC.getValues().valueMap().size())) throw new IllegalStateException("Unexpected configuration fields");
                GLFW.glfwHideWindow(mc.getWindow().getWindow()); mc.options.pauseOnLostFocus = false;
                mc.options.enableVsync().set(false); mc.options.framerateLimit().set(260); mc.options.renderDistance().set(6);
                if (Files.exists(directory.resolve("saves/single-round"))) throw new IllegalStateException("Existing world refused");
                creating = true;
                mc.createWorldOpenFlows().createFreshLevel("single-round", new LevelSettings("single-round", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(2067,false,false), r -> r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
                return;
            }
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            // DS camera hooks use the real player's interpolated yaw even with a fixture camera.
            if (camera != null && mc.getCameraEntity() == camera) {
                mc.player.setYRot(180); mc.player.yRotO=180;
                mc.player.setYHeadRot(180); mc.player.yHeadRotO=180;
                mc.player.setYBodyRot(180); mc.player.yBodyRotO=180;
            }
            ticks++;
            if (stage == 0) {
                if (setup == null) { setup = setModel(mc, 0); return; }
                if (!setup.isDone() || ticks < 100) return; setup.join();
                mc.setScreen(new ConfigurationScreen(ModList.get().getModContainerById("dsbr").orElseThrow(), null));
                stage = 1; ticks = 0; return;
            }
            if (stage == 1 && ticks >= 10) {
                screenshot(mc,"configuration");
                var config = new LinkedHashMap<String,Object>(); config.put("screen",mc.screen.getClass().getName()); config.put("fields",OptimizerConfig.SPEC.getValues().valueMap().keySet());
                OptimizerConfig.MESH_MIB.set(129); OptimizerConfig.SPEC.save();
                if (!Files.readString(directory.resolve("config/dsbr-optimizer-client.toml")).contains("meshBudgetMiB = 129")) throw new IllegalStateException("Config save failed");
                OptimizerConfig.MESH_MIB.set(128); OptimizerConfig.SPEC.save(); config.put("saveVerified",true); rows.add(Map.of("configuration",config));
                mc.setScreen(null); shader(mc,false); camera = new ArmorStand(mc.level,0,-55,20); camera.moveTo(0,-55,20,180,12); camera.setOldPosAndRot();
                mc.setCameraEntity(camera); mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = true;
                souls(mc,COUNTS[0],0,"idle",false); beginWarm(); stage = 2; return;
            }
            if (stage == 2) {
                if (System.nanoTime() < deadline) return;
                if (!sampling) { Diagnostics.INSTANCE.reset(); ProbeFrames.begin(); sampling = true; deadline = System.nanoTime()+15_000_000_000L; return; }
                String name = "souls-"+COUNTS[count]+"-"+(shader==0?"plain":"complementary");
                var stats=Diagnostics.INSTANCE.snapshot(); var totals=(Map<?,?>)stats.get("totals");
                check(number(totals,"SOUL_GPU_PASS")>0,name+": no soul GPU geometry");
                check(COUNTS[count]==1 || number(totals,"SOUL_GPU_PASS")>number(totals,"SOUL_GPU_DRAW_CALLS"),name+": soul instances were not merged");
                check(number(totals,"READBACK")==0,name+": unexpected normal texture readback");
                check(number(totals,"SOUL_TEXTURE_HIT")>0,name+": no shared soul texture hits");
                ProbeFrames.save(directory.resolve(name+".csv")); screenshot(mc,name);
                rows.add(Map.of("scenario",name,"souls",COUNTS[count],"warmupSeconds",5,"sampleSeconds",15,"repetitions",1,"stats",stats)); save();
                if (++count<COUNTS.length) { souls(mc,COUNTS[count],0,"idle",false); beginWarm(); return; }
                count=0;
                if (++shader==1) { shader(mc,true); souls(mc,COUNTS[0],0,"idle",false); beginWarm(); return; }
                stage=3; model=0; action=0; setup=null; ticks=0; return;
            }
            if (stage == 3) {
                if (setup == null) { setup=setModel(mc,model); return; }
                if (!setup.isDone()) return; setup.join();
                var h=DragonStateProvider.getData(mc.player);
                if (!h.speciesId().getPath().equals(MODELS[model][0])) return;
                souls(mc,4,model,ACTIONS[action],true);
                AnimationSamples.begin(MODELS[model][0]+"/"+ACTIONS[action]); stage=4; ticks=0; return;
            }
            if (stage == 4) {
                movement(mc,action);
                if (ticks<60) return;
                String name="animation-"+model+"-"+ACTIONS[action]; screenshot(mc,name);
                var samples=AnimationSamples.snapshot(); check(!samples.isEmpty(),name+": no real animation samples");
                var actors=new HashMap<String,List<Map<String,Object>>>();
                for(var sample:samples) actors.computeIfAbsent((String)sample.get("actor"),k->new ArrayList<>()).add(sample);
                for(var entry:actors.entrySet()) {
                    // An entity removed between ticks can contribute one final render sample.
                    // Such a transition cannot establish either continuity or a frozen animation.
                    if (entry.getKey().startsWith("soul-") && entry.getValue().size() < 2) continue;
                    check(entry.getValue().stream().map(r->r.get("tick")).distinct().count()>1,name+": frozen time for "+entry.getKey());
                    if(action>0) check(entry.getValue().stream().map(r->r.get("poseHash")).distinct().count()>1,name+": frozen pose for "+entry.getKey());
                    if(entry.getKey().startsWith("soul-")) check(entry.getValue().stream().anyMatch(r -> r.get("controllers").toString().contains("animation="+ACTIONS[action])),name+": incorrect soul animation for "+entry.getKey());
                }
                check(actors.keySet().stream().anyMatch(k->k.startsWith("soul-")),name+": no soul animation samples");
                check(actors.containsKey("player"),name+": player outside render scene");
                rows.add(Map.of("scenario",name,"samples",samples)); save();
                if (++action<ACTIONS.length) { stage=3; ticks=0; return; }
                action=0; if(++model<MODELS.length) { setup=null; stage=3; ticks=0; return; }
                model=0; setup=null; stage=9; ticks=0; mc.setCameraEntity(mc.player); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.options.hideGui=false; return;
            }
            if(stage==9) {
                if(setup==null){setup=setModel(mc,0);return;} if(!setup.isDone())return; setup.join();
                if(ticks<60)return;
                var magic=MagicData.getData(mc.player); var ability=magic.fromSlot(0);
                if(ability==null) throw new IllegalStateException("Breath slot not synchronized");
                mana(mc.player); ability.setCooldown(0);
                mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());mana(p);var a=MagicData.getData(p).fromSlot(0);if(a!=null)a.setCooldown(0);});
                if(!magic.attemptCast(mc.player,0))throw new IllegalStateException("Breath cast rejected");
                PacketDistributor.sendToServer(new by.dragonsurvivalteam.dragonsurvival.network.magic.SyncBeginCast(mc.player.getId(),0));
                AnimationSamples.begin("player/breath-and-recovery"); stage=10; ticks=0; return;
            }
            if(stage==10) {
                if(ticks==30) { check(MagicData.getData(mc.player).isCasting(),"Breath stopped before release"); screenshot(mc,"breath"); }
                if(ticks==50) { MagicData.getData(mc.player).stopCasting(mc.player); PacketDistributor.sendToServer(new by.dragonsurvivalteam.dragonsurvival.network.magic.SyncStopCast(mc.player.getId(),Optional.empty())); }
                if(ticks<80)return;
                check(!MagicData.getData(mc.player).isCasting(),"Breath did not recover after release"); screenshot(mc,"breath-recovery");
                var samples=AnimationSamples.snapshot();check(!samples.isEmpty(),"No breath animation samples");rows.add(Map.of("scenario","breath-and-recovery","samples",samples));save();
                // Creative InventoryScreen redirects to the creative tabs, which have no entity preview.
                mc.gameMode.setLocalMode(GameType.SURVIVAL);
                stage=5; ticks=0; mc.setScreen(new InventoryScreen(mc.player)); Diagnostics.INSTANCE.reset(); return;
            }
            if(stage==5 && ticks>=40) {
                screenshot(mc,"inventory"); var totals=(Map<?,?>)Diagnostics.INSTANCE.snapshot().get("totals"); check(number(totals,"GUI_CPU_PASS")>0,"Inventory did not use protected CPU preview");
                mc.setScreen(null); OptimizerConfig.MODE.set(OptimizerConfig.Mode.VANILLA); stage=6; ticks=0; return;
            }
            if(stage==6 && ticks>=10) { OptimizerConfig.MODE.set(OptimizerConfig.Mode.TEXTURES); stage=7; ticks=0; return; }
            if(stage==7 && ticks>=10) { OptimizerConfig.MODE.set(OptimizerConfig.Mode.GPU); reload=mc.reloadResourcePacks(); stage=8; ticks=0; return; }
            if(stage==8 && reload.isDone() && ticks>=60) {
                reload.join(); check(GpuDispatcher.enabled(),"GPU disabled after mode/reload lifecycle checks");
                finish(mc,null);
            }
        } catch(Throwable error) { finish(mc,error); }
    }
    static CompletableFuture<Void> setModel(Minecraft mc,int index) {
        return CompletableFuture.runAsync(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()); var h=DragonStateProvider.getData(p);
            var species=p.registryAccess().registryOrThrow(DragonSpecies.REGISTRY).getHolderOrThrow(ResourceKey.create(DragonSpecies.REGISTRY,ResourceLocation.fromNamespaceAndPath("dragonsurvival",MODELS[index][0])));
            var body=p.registryAccess().registryOrThrow(DragonBody.REGISTRY).getHolderOrThrow(ResourceKey.create(DragonBody.REGISTRY,ResourceLocation.fromNamespaceAndPath("dragonsurvival",MODELS[index][1])));
            h.setSpecies(p,species); h.setBody(p,body); h.setDesiredGrowth(p,index==0?40:100); h.setGrowth(p,index==0?40:100); h.isGrowthStopped=true;
            h.refreshSkinPresetForSpecies(species,body); h.getCurrentSkinPreset().setAllStagesToUseDefaultSkin(index!=0); h.getSkinData().blankSkin=false;
            h.getCurrentStageCustomization().layerSettings.get(by.dragonsurvivalteam.dragonsurvival.client.skin_editor_system.SkinLayer.EYES).get().isGlowing=true; h.recompileCurrentSkin();
            p.connection.teleport(5,-60,4,0,0); p.getAbilities().mayfly=true; p.onUpdateAbilities();
            p.serverLevel().setDayTime(6000); p.serverLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,mc.getSingleplayerServer());
            SyncComplete.handleDragonSync(p,false); PacketDistributor.sendToPlayersTrackingEntityAndSelf(p,new SyncComplete(p.getId(),h.serializeNBT(p.registryAccess())));
        },mc.getSingleplayerServer());
    }
    static void souls(Minecraft mc,int amount,int modelIndex,String animation,boolean varied) {
        for(var p:positions) mc.level.setBlock(p,Blocks.AIR.defaultBlockState(),3); positions.clear();
        var data=DragonStateProvider.getData(mc.player).serializeNBT(mc.player.registryAccess());
        for(int i=0;i<amount;i++) {
            var pos=new BlockPos((i%4)*3-5,-60,(i/4)*3);
            var state=DSBlocks.DRAGON_SOUL.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,varied?Direction.Plane.HORIZONTAL.stream().toList().get(i%4):Direction.NORTH);
            mc.level.setBlock(pos,state,3);
            var soul=(DragonSoulBlockEntity)mc.level.getBlockEntity(pos);
            if(soul==null) throw new IllegalStateException("Real soul block entity missing");
            soul.setComponents(DataComponentMap.builder().set(DSDataComponents.DRAGON_SOUL.get(),new DragonSoulData(data.copy(),new CompoundTag(),varied?.7+i*.15:1)).build());
            if(varied && modelIndex==0 && i==3) {
                var h=soul.getHandler(); var eyes=h.getCurrentStageCustomization().layerSettings.get(by.dragonsurvivalteam.dragonsurvival.client.skin_editor_system.SkinLayer.EYES).get();
                eyes.hue+=.15f;eyes.isModified=true;h.recompileCurrentSkin();
            }
            soul.animation=animation; positions.add(pos);
        }
    }
    private static void movement(Minecraft mc,int action) {
        mc.player.fallDistance=0; mc.player.getAbilities().flying=action==2;
        var move=MovementData.getData(mc.player); var velocity=new Vec3(action==0?0:.3,0,0);
        move.set(0,0,0,velocity); move.setDesiredMoveVec(action==0?Vec3.ZERO:new Vec3(1,0,0));
    }
    private static void shader(Minecraft mc,boolean enabled)throws Exception {
        var iris=Class.forName("net.irisshaders.iris.Iris"); var config=iris.getMethod("getIrisConfig").invoke(null);
        config.getClass().getMethod("setShaderPackName",String.class).invoke(config,"ComplementaryReimagined_r5.9.zip");
        config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,enabled); config.getClass().getMethod("save").invoke(config); iris.getMethod("reload").invoke(null);
    }
    private static long number(Map<?,?> map,String key){return map.get(key) instanceof Number n?n.longValue():0;}
    private static void mana(net.minecraft.world.entity.player.Player player) {
        try { var magic=MagicData.getData(player);
            try { magic.getClass().getMethod("setCurrentMana",net.minecraft.world.entity.player.Player.class,float.class).invoke(magic,player,1000f); }
            catch(NoSuchMethodException ignored){magic.getClass().getMethod("setCurrentMana",float.class).invoke(magic,1000f);}
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Mana setup failed",failure);}
    }
    private static void beginWarm(){sampling=false;deadline=System.nanoTime()+5_000_000_000L;}
    private static void check(boolean pass,String failure){if(!pass) failures.add(failure);}
    private static void screenshot(Minecraft mc,String name)throws Exception {try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(directory.resolve(name+".png"));}}
    private static void save()throws Exception {Files.writeString(directory.resolve("single-round-scenes.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(rows));}
    private static void finish(Minecraft mc,Throwable error) {
        done=true; AnimationSamples.label="";
        try {
            if(error!=null){failures.add(error.toString());RenderOptimizer.LOGGER.error("Single-round validation failed",error);}
            var stats=Diagnostics.INSTANCE.snapshot(); RenderOptimizer.clear();
            check(TextureCache.bytes()==0 && GpuDispatcher.bytes()==0,"Resources remained after final clear");
            save(); var result=new LinkedHashMap<String,Object>(); result.put("pass",failures.isEmpty()); result.put("failures",failures); result.put("repetitions",1); result.put("candidateOnly",true);
            result.put("textureBytesAfterClear",TextureCache.bytes());result.put("meshBytesAfterClear",GpuDispatcher.bytes());result.put("stats",stats);
            result.put("compatibility",top.wu949.dsbr.optimizer.compat.OptimizerMixinPlugin.status); result.put("scenes",rows.size());
            Files.writeString(directory.resolve("single-round-result.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
        }catch(Exception failure){failure.printStackTrace();}
        mc.stop();
    }
}
