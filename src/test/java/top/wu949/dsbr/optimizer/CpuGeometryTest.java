package top.wu949.dsbr.optimizer;

import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.joml.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.renderer.GeoRenderer;
import top.wu949.dsbr.optimizer.gpu.*;
import static org.junit.jupiter.api.Assertions.*;

class CpuGeometryTest {
    private static GeoRenderer<?> renderer(boolean scratch) {
        return (GeoRenderer<?>)Proxy.newProxyInstance(CpuGeometryTest.class.getClassLoader(),new Class[]{GeoRenderer.class},(proxy,method,args)->{
            if (scratch && method.getName().equals("createVerticesOfQuad")) {
                CpuGeometry.quad((GeoQuad)args[0],(Matrix4f)args[1],(Vector3f)args[2],(VertexConsumer)args[3],(int)args[4],(int)args[5],(int)args[6]); return null;
            }
            if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,args);
            throw new AssertionError("Unexpected renderer call: "+method);
        });
    }
    private static VertexConsumer recorder(List<List<Object>> rows) {
        return (VertexConsumer)Proxy.newProxyInstance(CpuGeometryTest.class.getClassLoader(),new Class[]{VertexConsumer.class},(proxy,method,args)->{
            if(method.getName().equals("addVertex") && args.length==11){rows.add(List.of(args.clone()));return null;}
            throw new AssertionError("Consumer calls changed: "+method);
        });
    }
    @Test void allConsumerValuesMatchOriginalForPivotRotationNegativeScaleAndFlatCubes() {
        var vertices=new GeoVertex[]{new GeoVertex(new Vector3f(-1,2,3),0,.2f),new GeoVertex(new Vector3f(4,-5,6),.2f,.3f),new GeoVertex(new Vector3f(2,8,-9),.9f,.4f),new GeoVertex(new Vector3f(5,3,7),1,1)};
        for(var normal:List.of(new Vector3f(-1,0,0),new Vector3f(0,-1,0),new Vector3f(0,0,-1)))
            for(var size:List.of(new Vec3(1,2,3),new Vec3(0,2,3),new Vec3(1,0,3),Vec3.ZERO)) {
                var cube=new GeoCube(new GeoQuad[]{new GeoQuad(vertices,normal,Direction.DOWN),null},new Vec3(3,4,5),new Vec3(.3,-.5,.9),size,0,false);
                var a=new PoseStack();var b=new PoseStack();
                for(var stack:List.of(a,b)){stack.translate(4,-3,8);stack.mulPose(new Quaternionf().rotationXYZ(.5f,.3f,-.2f));stack.scale(-.75f,2,.5f);}
                var expected=new ArrayList<List<Object>>();var actual=new ArrayList<List<Object>>();
                renderer(false).renderCube(a,cube,recorder(expected),0xf000f0,0x12345678,0x80112233);
                CpuGeometry.cube(renderer(true),b,cube,recorder(actual),0xf000f0,0x12345678,0x80112233);
                assertEquals(expected,actual);assertEquals(a.last().pose(),b.last().pose());assertEquals(a.last().normal(),b.last().normal());
            }
        CpuVertexScratch.INSTANCE.clear();
    }
}
