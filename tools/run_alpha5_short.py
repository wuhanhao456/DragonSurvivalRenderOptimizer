"""Bounded paired exploratory suite; no profiler, no account or remote-server access."""
import argparse, hashlib, json, pathlib, subprocess, sys, time
from low_metrics import metrics
from run_low_suite import alive, tree_hash

PROJECT=pathlib.Path(__file__).resolve().parents[1]
SCENES=['players-1','players-4','players-12','mo-4','dihuang-4','mixed-12','souls17-mo4']
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def read(path):return json.loads(path.read_text(encoding='utf-8'))
def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')

def summarize(root,version,jar,scene,sample):
    result=read(root/'alpha5-result.json')
    if not result['pass']:raise RuntimeError(result)
    if result['meshBytesAfterClear'] or result['textureBytesAfterClear']:raise ValueError('Cleanup failed')
    row=read(root/'alpha5-scenes.json')[0]
    if digest(root/'mods/optimizer.jar')!=digest(jar):raise ValueError('Wrong tested jar')
    data=metrics(root/row['rawFrames'])
    if data['capturedSeconds']<sample*.99:raise ValueError('Incomplete frame sampling')
    if (row['width'],row['height'],row['renderDistance'],row['effectiveFpsLimit'],row['swapInterval'])!=(1280,720,6,260,0) or row['vsync']:raise ValueError('Display settings changed')
    if row['shaderPack']!='ComplementaryUnbound_r5.9.zip':raise ValueError('Shader changed')
    if row['camera']!=[0.0,-52.0,36.0,180.0,13.0]:raise ValueError('Camera changed')
    if (root/'diagnostic.jfr').exists():raise ValueError('Profiler enabled during comparison')
    spark=root/'config/spark/config.json'
    if spark.exists() and read(spark).get('backgroundProfiler',True):raise ValueError('Spark profiler enabled')
    totals=row['stats']['totals'];frames=row['stats']['frameSamples']
    if totals.get('GENERATED',0) or totals.get('READBACK',0):raise ValueError('Steady texture maintenance regression')
    if row['stats']['fallbacks']:raise ValueError('GPU feature failed: '+str(row['stats']['fallbacks']))
    if version=='candidate' and not scene.startswith('players-'):
        count=12 if scene=='mixed-12' else 4
        if totals.get('NPC_GPU_PASS',0)<frames*count*.98:raise ValueError('NPCs missing from GPU scene')
        if totals.get('NPC_CPU_BONE',0):raise ValueError('NPC vertex submission did not use GPU')
    if scene.startswith('players-') and totals.get('GPU_PASS',0)<frames*int(scene[8:])*1.8:raise ValueError('Dragon players missing from scene')
    return {'scene':scene,'version':version,'jarSha256':digest(jar),'driverSha256':digest(root/'mods/validation.jar'),'metrics':data,'row':row,'result':result,
            'instance':str(root),'rawSha256':digest(root/row['rawFrames'])}

def run(a,version,scene):
    jar=a.baseline if version=='baseline' else a.candidate
    common=['--runtime',str(a.runtime),'--build',str(a.build),'--pack',str(a.pack),'--manifest',str(a.manifest),'--game-jar',str(a.game_jar),'--java',str(a.java),
        '--fullpack','--iris','--shaderpacks','--shader','ComplementaryUnbound_r5.9.zip','--alpha5-scenes',scene,'--warm',str(a.warm),'--sample',str(a.sample),'--heap','8G',
        '--optimizer-jar',str(jar),'--world-template',str(a.world)]
    completed=subprocess.run([sys.executable,str(PROJECT/'tools/launch_probe.py'),*common],capture_output=True,text=True,encoding='utf-8',cwd=PROJECT)
    if completed.returncode:raise RuntimeError(completed.stderr)
    record=json.loads(completed.stdout);root=pathlib.Path(record['instance']);deadline=time.time()+900
    write(a.output/'active-launch.json',{'scene':scene,'version':version,**record})
    print(json.dumps({'event':'started','scene':scene,'version':version,'pid':record['pid'],'instance':str(root)}),flush=True)
    while not (root/'alpha5-result.json').exists():
        if not alive(record['pid']):raise RuntimeError('Probe exited without result: '+str(root))
        if time.time()>deadline:raise TimeoutError('Our isolated probe exceeded 15 minutes: '+str(root))
        time.sleep(2)
    row=summarize(root,version,jar,scene,a.sample)
    stop=time.time()+120
    while alive(record['pid']) and time.time()<stop:time.sleep(1)
    if alive(record['pid']):raise TimeoutError('Our probe did not exit after saving')
    write(a.output/(scene+'-'+version+'.json'),row)
    print(json.dumps({'event':'sampled','scene':scene,'version':version,'metrics':row['metrics']}),flush=True)
    return row

def compare(b,c):
    bm,cm=b['metrics'],c['metrics']
    change=lambda new,old:(new/old-1)*100
    result={'scene':b['scene'],'averageFpsChangePercent':change(cm['averageFps'],bm['averageFps']),
        'low1ChangePercent':change(cm['low1Fps'],bm['low1Fps']),'low01ChangePercent':change(cm['low01Fps'],bm['low01Fps']),
        'p95ChangePercent':change(cm['frameTimeMs']['p95'],bm['frameTimeMs']['p95']),'p99ChangePercent':change(cm['frameTimeMs']['p99'],bm['frameTimeMs']['p99'])}
    if b['row']['npcGeometryCalls']:
        before=b['row']['npcGeometryNanos']/b['row']['npcGeometryCalls'];after=c['row']['npcGeometryNanos']/c['row']['npcGeometryCalls']
        result['npcGeometryCpuReductionPercent']=(1-after/before)*100
    result['recheckRequired']=result['averageFpsChangePercent'] < -5 or result['p95ChangePercent'] > 5
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser()
    for name in ('baseline','candidate','runtime','build','pack','manifest','game-jar','java','world','output'):p.add_argument('--'+name,type=pathlib.Path,required=True)
    p.add_argument('--scenes',default=','.join(SCENES));p.add_argument('--warm',type=int,default=15);p.add_argument('--sample',type=int,default=60)
    a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
    rows=[];comparisons=[];world_hash=tree_hash(a.world)
    try:
        for i,scene in enumerate(a.scenes.split(',')):
            if scene not in SCENES:raise ValueError('Unknown scene')
            pair={}
            for version in (['baseline','candidate'] if i%2==0 else ['candidate','baseline']):pair[version]=run(a,version,scene);rows.append(pair[version])
            if tree_hash(a.world)!=world_hash:raise ValueError('Source world was modified')
            comparisons.append(compare(pair['baseline'],pair['candidate']))
            write(a.output/'suite.json',{'complete':False,'exploratory':True,'worldSha256':world_hash,'warmupSeconds':a.warm,'sampleSeconds':a.sample,'repetitions':1,'rows':rows,'comparisons':comparisons})
        write(a.output/'suite.json',{'complete':True,'exploratory':True,'worldSha256':world_hash,'warmupSeconds':a.warm,'sampleSeconds':a.sample,'repetitions':1,'rows':rows,'comparisons':comparisons})
    except Exception as error:
        write(a.output/'suite.json',{'complete':False,'exploratory':True,'error':str(error),'rows':rows,'comparisons':comparisons});raise
