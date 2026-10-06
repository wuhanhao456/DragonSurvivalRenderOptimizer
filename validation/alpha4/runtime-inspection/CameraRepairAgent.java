import java.lang.instrument.*;import java.lang.reflect.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
public class CameraRepairAgent {
 public static void agentmain(String out, Instrumentation inst)throws Exception {
  Class<?> mcType=Arrays.stream(inst.getAllLoadedClasses()).filter(c->c.getName().equals("net.minecraft.client.Minecraft")).findFirst().orElseThrow();Object mc=mcType.getMethod("getInstance").invoke(null);
  var executor=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"DSBR fixture camera repair");t.setDaemon(true);return t;});
  executor.scheduleAtFixedRate(()->{try{
   if(Files.exists(Path.of(out).getParent().resolve("single-round-result.json"))){executor.shutdown();return;}
   mcType.getMethod("execute",Runnable.class).invoke(mc,(Runnable)()->{try{Object p=mcType.getField("player").get(mc); if(p==null)return;Object e=mcType.getMethod("getCameraEntity").invoke(mc);if(e==p)return;
    p.getClass().getMethod("setYRot",float.class).invoke(p,180f);p.getClass().getField("yRotO").setFloat(p,180f);p.getClass().getMethod("setYHeadRot",float.class).invoke(p,180f);p.getClass().getField("yHeadRotO").setFloat(p,180f);p.getClass().getMethod("setYBodyRot",float.class).invoke(p,180f);p.getClass().getField("yBodyRotO").setFloat(p,180f);
    Files.writeString(Path.of(out),"Fixture camera correction: Dragon Survival camera follows the real player's interpolated yaw even for the ArmorStand camera. Player yaw fixed at 180 degrees. No scene repeated.\n");
   }catch(Throwable x){try{Files.writeString(Path.of(out),x.toString());}catch(Exception y){}}});
  }catch(Exception x){executor.shutdown();}},0,50,TimeUnit.MILLISECONDS);
 }
}
