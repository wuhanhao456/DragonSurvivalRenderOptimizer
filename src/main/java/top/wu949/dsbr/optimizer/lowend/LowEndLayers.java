package top.wu949.dsbr.optimizer.lowend;
import java.util.*;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import top.wu949.dsbr.optimizer.compat.RendererAdmission;
import top.wu949.dsbr.optimizer.diagnostics.Diagnostics;
public final class LowEndLayers {
    private record Filter(List<?> source, Object[] identities, List<?> result) {}
    private static final Map<Object,Filter> cache=new IdentityHashMap<>();
    @SuppressWarnings({"unchecked","rawtypes"})
    public static List<GeoRenderLayer<?>> filter(Object renderer,List<GeoRenderLayer<?>> source) {
        if(!LowEndSupport.enabled() || LowEndSupport.editor() || RendererAdmission.kind(renderer)==RendererAdmission.Kind.OTHER) return source;
        var old=cache.get(renderer);boolean match=old!=null && old.source==source && old.identities.length==source.size();
        if(match)for(int i=0;i<source.size();i++)if(old.identities[i]!=source.get(i)){match=false;break;}
        if(!match){
            var reduced=source.stream().filter(layer->{String name=layer.getClass().getName();return !(name.endsWith(".DragonGlowLayerRenderer")||name.endsWith(".DragonArmorRenderLayer")||name.endsWith(".DragonBackpackRenderLayer"));}).toList();
            if(cache.size()>=128)cache.clear();cache.put(renderer,old=new Filter(source,source.toArray(),reduced));
        }
        if(old.result.size()!=source.size())Diagnostics.INSTANCE.count(Diagnostics.Counter.LOW_END_LAYER_SKIPPED,"layer");
        return (List)old.result;
    }
    public static void clear(){cache.clear();}
    private LowEndLayers(){}
}
