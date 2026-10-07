"""Bounded isolated compatibility/visual runs, separate from the paired timing suite."""
import argparse,json,pathlib,subprocess,sys,time,hashlib
from run_low_suite import alive
PROJECT=pathlib.Path(__file__).resolve().parents[1]
def read(p):return json.loads(p.read_text(encoding='utf-8'))
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def run(a,name,flags,result_name):
    common=['--runtime',str(a.runtime),'--build',str(a.build),'--pack',str(a.pack),'--manifest',str(a.manifest),'--game-jar',str(a.game_jar),'--java',str(a.java),'--optimizer-jar',str(a.candidate)]
    proc=subprocess.run([sys.executable,str(PROJECT/'tools/launch_probe.py'),*common,*flags],cwd=PROJECT,capture_output=True,text=True,encoding='utf-8')
    if proc.returncode:raise RuntimeError(proc.stderr)
    record=json.loads(proc.stdout);root=pathlib.Path(record['instance']);deadline=time.time()+900
    print(json.dumps({'event':'runtime-started','test':name,**record}),flush=True)
    while not (root/result_name).exists():
        if not alive(record['pid']):raise RuntimeError('Exited without result: '+str(root))
        if time.time()>deadline:raise TimeoutError('Own probe exceeded 15 min: '+str(root))
        time.sleep(2)
    result=read(root/result_name)
    if not result['pass']:raise RuntimeError(result)
    if digest(root/'mods/optimizer.jar')!=digest(a.candidate):raise ValueError('Wrong runtime jar')
    end=time.time()+120
    while alive(record['pid']) and time.time()<end:time.sleep(1)
    if alive(record['pid']):raise TimeoutError('Own probe did not exit')
    if name=='no-core':
        assert 'NPC=absent' in result['compatibility'] and 'GPU=true' in result['compatibility']
        ids=[p.name for p in (root/'mods').glob('*.jar')]
        assert len(ids)==4,ids # optimizer, test-only driver, DS and Gecko
    if name=='mismatch':
        assert 'unsupported Core version 0.10.2' in result['compatibility'] and 'GPU=true' in result['compatibility']
        totals=read(root/'alpha5-scenes.json')[0]['stats']['totals'];assert totals.get('GPU_PASS',0)>0 and not totals.get('NPC_GPU_PASS',0)
    if name=='npc-final':
        assert 'NPC=Core 0.10.1 NPC interfaces verified' in result['compatibility']
        for row in read(root/'alpha5-scenes.json'):
            totals=row['stats']['totals'];assert totals.get('NPC_GPU_PASS',0)>=row['stats']['frameSamples']*4*.98 and not totals.get('NPC_CPU_BONE',0) and not totals.get('READBACK',0)
    print(json.dumps({'event':'runtime-passed','test':name,'instance':str(root),'result':result}),flush=True)
    return {'test':name,'instance':str(root),'jarSha256':digest(a.candidate),'driverSha256':digest(root/'mods/validation.jar'),'result':result}
if __name__=='__main__':
    p=argparse.ArgumentParser()
    for n in ('candidate','runtime','build','pack','manifest','game-jar','java','world','mismatch-fixture','output'):p.add_argument('--'+n,type=pathlib.Path,required=True)
    p.add_argument('--tests',default='no-core,absent,mismatch,npc-smoke,ds-actions');a=p.parse_args();rows=[];a.output.mkdir(parents=True,exist_ok=True)
    tests={
        'no-core':(['--heap','4G'],'probe-result.json'),
        'absent':(['--compat-only','--heap','4G'],'probe-result.json'),
        'mismatch':(['--fullpack','--iris','--shaderpacks','--shader','ComplementaryUnbound_r5.9.zip','--heap','8G','--world-template',str(a.world),'--alpha5-scenes','mo-4','--warm','5','--sample','5','--fixture-mods',str(a.mismatch_fixture)],'alpha5-result.json'),
        'npc-smoke':(['--fullpack','--iris','--shaderpacks','--heap','8G','--world-template',str(a.world),'--npc-smoke'],'npc-smoke-result.json'),
        'npc-lifecycle':(['--fullpack','--iris','--shaderpacks','--heap','8G','--world-template',str(a.world),'--npc-smoke','--npc-lifecycle-only'],'npc-smoke-result.json'),
        'reload-diagnostic':(['--fullpack','--iris','--shaderpacks','--shader','ComplementaryUnbound_r5.9.zip','--heap','8G','--world-template',str(a.world),'--reload-diagnostic'],'reload-diagnostic-result.json'),
        'ds-actions':(['--fullpack','--iris','--shaderpacks','--heap','8G','--single-round'],'single-round-result.json')}
    tests['ds-actions-recheck']=(['--fullpack','--iris','--shaderpacks','--heap','8G','--single-round','--single-round-actions-only'],'single-round-result.json')
    tests['npc-final']=(['--fullpack','--iris','--shaderpacks','--shader','ComplementaryUnbound_r5.9.zip','--heap','8G','--world-template',str(a.world),'--alpha5-scenes','mo-4,dihuang-4','--warm','5','--sample','5'],'alpha5-result.json')
    try:
        for name in a.tests.split(','):
            row=run(a,name,*tests[name]);rows.append(row);(a.output/'runtime.json').write_text(json.dumps({'complete':False,'runs':rows},ensure_ascii=False,indent=2),encoding='utf-8')
        (a.output/'runtime.json').write_text(json.dumps({'complete':True,'runs':rows},ensure_ascii=False,indent=2),encoding='utf-8')
    except Exception as e:
        (a.output/'runtime.json').write_text(json.dumps({'complete':False,'error':str(e),'runs':rows},ensure_ascii=False,indent=2),encoding='utf-8');raise
