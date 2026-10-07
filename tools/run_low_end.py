"""One bounded off/on paired suite using the same final client and frozen resources."""
import argparse,hashlib,json,pathlib,subprocess,sys,time
from low_metrics import metrics
from run_low_suite import alive
PROJECT=pathlib.Path(__file__).resolve().parents[1]
SCENES='players-1-off,players-1-on,players-12-on,players-12-off,souls17-npc8-off,souls17-npc8-on'
def read(p):return json.loads(p.read_text(encoding='utf-8'))
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding='utf-8')
def run(a,shader):
 flags=['--build',str(a.build),'--pack',str(a.pack),'--fullpack','--iris','--low-end-scenes',a.scenes,'--warm','15','--sample','60','--heap','8G']
 if shader:flags+=['--shaderpacks','--shader','ComplementaryUnbound_r5.9.zip']
 process=subprocess.run([sys.executable,str(PROJECT/'tools/launch_probe.py'),*flags],cwd=PROJECT,capture_output=True,text=True,encoding='utf-8')
 if process.returncode:raise RuntimeError(process.stderr)
 launch=json.loads(process.stdout);root=pathlib.Path(launch['instance']);write(a.output/'active-launch.json',{'shader':shader,**launch})
 print(json.dumps({'event':'started','shader':shader,**launch}),flush=True)
 deadline=time.time()+1200
 while not (root/'lowend-result.json').exists():
  if not alive(launch['pid']):raise RuntimeError('Probe exited without result: '+str(root))
  if time.time()>deadline:raise TimeoutError('Our bounded suite exceeded 20 minutes: '+str(root))
  time.sleep(2)
 result=read(root/'lowend-result.json')
 if not result['pass']:raise RuntimeError(result)
 assert result['meshBytesAfterClear']==result['textureBytesAfterClear']==result['animationBytesAfterClear']==0
 jar=a.build/'libs/dsro-0.2.1.jar';assert digest(jar)==digest(root/'mods/optimizer.jar')
 rows=[];comparisons=[]
 for row in read(root/'lowend-scenes.json'):
  t=row['stats']['totals'];assert not t.get('READBACK',0)
  assert (row['width'],row['height'],row['renderDistance'],row['effectiveFpsLimit'],row['swapInterval'])==(1280,720,6,260,0) and not row['vsync']
  assert row['camera']==[0.0,-52.0,36.0,180.0,13.0]
  if shader:assert row['shaderPack']=='ComplementaryUnbound_r5.9.zip'
  if row['scene'].startswith('players-12'):assert len({actor['model'] for actor in row['actors']})==3
  m=metrics(root/row['rawFrames']);assert m['capturedSeconds']>=59.4
  if row['lowEnd']:
   assert t.get('LOW_END_ANIMATION_UPDATE',0)>0 and t.get('LOW_END_ANIMATION_REUSED',0)>t.get('LOW_END_ANIMATION_UPDATE',0)
   assert t.get('LOW_END_SKIN_BASE',0)>0 and not t.get('LOW_END_ANIMATION_FALLBACK',0)
   if row['scene'].startswith('souls'):assert t.get('LOW_END_SOUL_SKIPPED',0)>0 and t.get('SOUL_GPU_PASS',0)==0
  else:
   assert not t.get('LOW_END_ANIMATION_UPDATE',0) and not t.get('LOW_END_SOUL_SKIPPED',0)
   if row['scene'].startswith('souls'):assert t.get('SOUL_GPU_PASS',0)>0
  rows.append({'scene':row['scene'],'shader':shader,'metrics':m,'row':row,'jarSha256':digest(jar),'driverSha256':digest(root/'mods/validation.jar'),'rawSha256':digest(root/row['rawFrames']),'instance':str(root)})
 for scene in dict.fromkeys(r['scene'].removesuffix('-off').removesuffix('-on') for r in rows):
  before=next(r for r in rows if r['scene']==scene+'-off');after=next(r for r in rows if r['scene']==scene+'-on');assert before['row']['actors']==after['row']['actors']
  b=before['metrics'];c=after['metrics']
  change=lambda x,y:(x/y-1)*100
  pair={'scene':scene,'shader':shader,'averageFpsChangePercent':change(c['averageFps'],b['averageFps']),'low1ChangePercent':change(c['low1Fps'],b['low1Fps']),'low01ChangePercent':change(c['low01Fps'],b['low01Fps']),'p95ChangePercent':change(c['frameTimeMs']['p95'],b['frameTimeMs']['p95'])}
  pair['pass']=pair['averageFpsChangePercent']>=-5 and pair['low1ChangePercent']>=-5;comparisons.append(pair)
 write(a.output/('shader.json' if shader else 'plain.json'),{'rows':rows,'comparisons':comparisons,'launch':launch,'result':result})
 stop=time.time()+120
 while alive(launch['pid']) and time.time()<stop:time.sleep(1)
 assert not alive(launch['pid'])
 print(json.dumps({'event':'completed','shader':shader,'comparisons':comparisons}),flush=True)
 return {'rows':rows,'comparisons':comparisons,'launch':launch,'result':result}
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--build',type=pathlib.Path,required=True);p.add_argument('--pack',type=pathlib.Path,required=True);p.add_argument('--output',type=pathlib.Path,required=True);p.add_argument('--shaders',default='plain,shader');p.add_argument('--scenes',default=SCENES);a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
 selected=a.scenes.split(',')
 for scene in dict.fromkeys(s.removesuffix('-off').removesuffix('-on') for s in selected):
  assert selected.count(scene+'-off')==selected.count(scene+'-on')==1, 'Each selected scene requires one off/on pair'
 results=[]
 for shader in a.shaders.split(','):results.append(run(a,shader=='shader'));write(a.output/'suite.json',{'complete':len(results)==2 and a.scenes==SCENES,'targeted':a.scenes!=SCENES,'repetitions':1,'warmupSeconds':15,'sampleSeconds':60,'results':results,'pass':all(c['pass'] for r in results for c in r['comparisons'])})
