import java.lang.instrument.*;import java.lang.reflect.*;import java.nio.file.*;import java.util.*;
public class InspectAgent {
 public static void agentmain(String out, Instrumentation inst)throws Exception {
  Class<?> mcType=Arrays.stream(inst.getAllLoadedClasses()).filter(c->c.getName().equals("net.minecraft.client.Minecraft")).findFirst().orElseThrow();
  Object mc=mcType.getMethod("getInstance").invoke(null);
  mcType.getMethod("execute",Runnable.class).invoke(mc,(Runnable)()->{try{
   StringBuilder s=new StringBuilder(); Object e=mcType.getMethod("getCameraEntity").invoke(mc);Object g=mcType.getField("gameRenderer").get(mc);Object c=g.getClass().getMethod("getMainCamera").invoke(g);
   for(Object o:List.of(e,c,mcType.getField("player").get(mc))){s.append(o.getClass()).append("\n");for(String n:List.of("getX","getY","getZ","getXRot","getYRot","getPosition","getEyeHeight")){try{s.append(n).append("=").append(o.getClass().getMethod(n).invoke(o)).append("\n");}catch(Exception x){}}}
   for(Class<?> t:inst.getAllLoadedClasses())if(t.getName().endsWith("SingleRoundProbe")){for(String n:List.of("stage","ticks","count","shader","model","action","failures","positions")){Field f=t.getDeclaredField(n);f.setAccessible(true);s.append(n).append("=").append(f.get(null)).append("\n");}}
   Files.writeString(Path.of(out),s);
  }catch(Throwable x){try{Files.writeString(Path.of(out),x.toString());}catch(Exception y){}}});
 }
}
