"""Sequential isolated A/B runs. Exact raw capture, jar identity, and acceptance evidence."""
import argparse, hashlib, json, os, pathlib, statistics, subprocess, sys, time
from low_metrics import metrics

project=pathlib.Path(__file__).resolve().parents[1]
def alive(pid):
    import ctypes
    kernel=ctypes.WinDLL('kernel32',use_last_error=True)
    kernel.OpenProcess.restype=ctypes.c_void_p
    kernel.WaitForSingleObject.argtypes=[ctypes.c_void_p,ctypes.c_ulong]
    kernel.CloseHandle.argtypes=[ctypes.c_void_p]
    handle=kernel.OpenProcess(0x100000,False,pid)
    if not handle: return False
    try: return kernel.WaitForSingleObject(handle,0)==258
    finally: kernel.CloseHandle(handle)
def read(p): return json.loads(p.read_text(encoding='utf-8'))
def file_hash(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def tree_hash(root):
    if root is None: return None
    digest=hashlib.sha256()
    for path in sorted(root.rglob('*')):
        if path.is_file():
            digest.update(path.relative_to(root).as_posix().encode('utf-8')); digest.update(b'\0')
            digest.update(bytes.fromhex(file_hash(path)))
    return digest.hexdigest()
def launch(arguments):
    result=subprocess.run([sys.executable,str(project/'tools/launch_probe.py'),*arguments],cwd=project,capture_output=True,text=True,encoding='utf-8')
    if result.returncode: raise RuntimeError(result.stderr or result.stdout)
    record=json.loads(result.stdout); root=pathlib.Path(record['instance']); deadline=time.time()+3600
    print(json.dumps({'event':'launch','instance':str(root),'pid':record['pid'],'arguments':arguments}),flush=True)
    while time.time()<deadline:
        path=root/'multi-result.json'
        if path.exists():
            result=read(path)
            if not result['pass']: raise RuntimeError(result)
            if result['textureBytesAfterClear'] or result['meshBytesAfterClear']: raise RuntimeError('Resource leak')
            # Wait for our own client to finish saving, before starting the next one.
            end=time.time()+120
            while alive(record['pid']) and time.time()<end: time.sleep(1)
            if alive(record['pid']): raise RuntimeError('Our probe failed to exit after cleanup')
            return root
        if not alive(record['pid']): raise RuntimeError('Probe exited without result: '+str(root))
        time.sleep(2)
    raise TimeoutError('Probe timed out: '+str(root))

def summarize(root):
    digest=hashlib.sha256((root/'mods/optimizer.jar').read_bytes()).hexdigest(); results=[]
    jvm_memory=[line.strip(chr(34)) for line in (root/'launch-args.txt').read_text(encoding='utf-8').splitlines() if line.strip(chr(34)).startswith(('-Xmx','-Xms'))]
    fullpack=(root/'fixture-mods.json').exists()
    if [x for x in jvm_memory if x.startswith('-Xmx')]!=['-Xmx8G' if fullpack else '-Xmx4G']: raise ValueError('Unexpected JVM heap flags')
    if fullpack:
        selection=read(root/'fixture-mods.json')
        core=[x for x in selection['selected'] if 'beloong' in x['ids']]
        if len(core)!=1 or core[0]['version']!='0.10.1': raise ValueError('Core fixture version or deduplication changed')
    for row in read(root/'multi-comparison.json'):
        m=metrics(root/row['rawFrames'])
        if m['capturedSeconds'] < row['samplingSeconds']*.99: raise ValueError('Capture too short')
        if (row['width'],row['height'],row['renderDistance'],row['fpsLimit'])!=(1280,720,6,260): raise ValueError('Settings changed')
        if row['vsync'] or row['shaderPack']!='ComplementaryReimagined_r5.9.zip': raise ValueError('Controlled display/shader settings changed')
        if row['windowFpsLimit']!=260 or row['effectiveFpsLimit']!=260 or row['swapInterval']!=0: raise ValueError('Actual window still capped or synchronized')
        if (root/'diagnostic.jfr').exists(): raise ValueError('Profiler recording present in formal sample')
        spark=root/'config/spark/config.json'
        if spark.exists() and read(spark).get('backgroundProfiler',True): raise ValueError('Spark background profiler enabled')
        results.append({**row,'metrics':m,'jvmMemoryFlags':jvm_memory,'jarSha256':digest,'instance':str(root), 'rawSha256':hashlib.sha256((root/row['rawFrames']).read_bytes()).hexdigest(),
                        'driverSha256':hashlib.sha256((root/'mods/validation.jar').read_bytes()).hexdigest(),
                        'fixtureSha256':hashlib.sha256((root/'fixture-provenance.json').read_bytes()).hexdigest() if (root/'fixture-provenance.json').exists() else None})
    return results

def acceptance(rows):
    if not rows: return {'complete':False,'pass':False,'groups':[]}
    groups=[]
    for scenario,count in sorted({(r['scenario'],r['dragonPlayers']) for r in rows}):
        subset=[r for r in rows if (r['scenario'],r['dragonPlayers'])==(scenario,count)]
        pairs=[]
        for repeat in (1,2,3):
            pair={r['versionLabel']:r for r in subset if r['pairRepetition']==repeat}
            if set(pair)!= {'alpha.2','alpha.3'}: return {'complete':False,'pass':False,'groups':groups}
            a,b=(pair[v]['metrics'] for v in ('alpha.2','alpha.3'))
            pairs.append({k:(b[k]/a[k]-1)*100 for k in ('averageFps','low1Fps','low01Fps')} | {k+'Ms':(b['frameTimeMs'][k]/a['frameTimeMs'][k]-1)*100 for k in ('p95','p99')})
        median={k:statistics.median(p[k] for p in pairs) for k in pairs[0]}
        passed=median['low01Fps']>=-5 and median['averageFps']>=-5 and median['p95Ms']<=5 and median['p99Ms']<=5
        if scenario=='fixed' and count in (4,12): passed &= median['low1Fps']>=10
        groups.append({'scenario':scenario,'dragonPlayers':count,'medianPairedChangePercent':median,'pass':passed})
    return {'complete':True,'pass':all(g['pass'] for g in groups),'groups':groups}

def main():
    p=argparse.ArgumentParser(); p.add_argument('--baseline',required=True,type=pathlib.Path); p.add_argument('--candidate',required=True,type=pathlib.Path)
    p.add_argument('--output',required=True,type=pathlib.Path); p.add_argument('--warm',type=int,default=60); p.add_argument('--sample',type=int,default=300)
    p.add_argument('--repeats',type=int,default=3); p.add_argument('--fullpack',action='store_true'); p.add_argument('--counts',default='1,4,12'); p.add_argument('--scenario',default='fixed')
    p.add_argument('--world-template',type=pathlib.Path); p.add_argument('--pack',type=pathlib.Path); p.add_argument('--group-counts',action='store_true'); a=p.parse_args(); a.output.mkdir(parents=True,exist_ok=True)
    manifest=a.output/'runs.json'; rows=read(manifest) if manifest.exists() else []
    hashes={label:hashlib.sha256(jar.read_bytes()).hexdigest() for label,jar in [('alpha.2',a.baseline),('alpha.3',a.candidate)]}
    driver=pathlib.Path(os.environ['LOCALAPPDATA'])/'Temp/DSBR-build/validation/dsbr-render-validation.jar'
    inputs={'jars':hashes,'driverSha256':file_hash(driver),'fixtureSha256':file_hash(a.pack/'fixture-provenance.json') if a.pack and (a.pack/'fixture-provenance.json').exists() else None,
            'launcherSha256':file_hash(project/'tools/launch_probe.py'),
            'worldTemplateSha256':tree_hash(a.world_template),'counts':a.counts,'scenario':a.scenario,'fullpack':a.fullpack,'warm':a.warm,'sample':a.sample,'repeats':a.repeats,
            'heap':'8G' if a.fullpack else '4G','groupCounts':a.group_counts}
    input_path=a.output/'inputs.json'
    if input_path.exists() and read(input_path)!=inputs: raise ValueError('Resume driver, fixture, world, or test settings changed')
    if rows and not input_path.exists(): raise ValueError('Existing runs lack a frozen input manifest')
    input_path.write_text(json.dumps(inputs,ensure_ascii=False,indent=2),encoding='utf-8')
    for row in rows:
        if row['jarSha256']!=hashes[row['versionLabel']] or row['scenario']!=a.scenario or row['fullpack']!=a.fullpack or row['warmupSeconds']!=a.warm or row['samplingSeconds']!=a.sample:
            raise ValueError('Resume inputs differ from recorded run')
        if row['driverSha256']!=inputs['driverSha256'] or row['fixtureSha256']!=inputs['fixtureSha256']: raise ValueError('Recorded driver or fixture differs')
    for repeat in range(1,a.repeats+1):
        counts=list(map(int,a.counts.split(',')))
        for group in ([counts] if a.group_counts else [[count] for count in counts]):
            for label,jar in ([('alpha.2',a.baseline),('alpha.3',a.candidate)] if repeat%2 else [('alpha.3',a.candidate),('alpha.2',a.baseline)]):
                pending=[count for count in group if not any(r['pairRepetition']==repeat and r['dragonPlayers']==count and r['versionLabel']==label for r in rows)]
                if not pending: continue
                args=['--iris','--shaderpacks','--multi-dragon-benchmark','--gpu-only','--counts',','.join(map(str,pending)),'--warm',str(a.warm),'--sample',str(a.sample),'--repeats','1','--optimizer-jar',str(jar),'--scenario',a.scenario]
                if a.fullpack: args+=['--fullpack','--heap','8G']
                if a.world_template: args+=['--world-template',str(a.world_template)]
                if a.pack: args+=['--pack',str(a.pack)]
                root=launch(args)
                for row in summarize(root):
                    if row['jarSha256']!=hashes[label] or row['driverSha256']!=inputs['driverSha256'] or row['fixtureSha256']!=inputs['fixtureSha256']:
                        raise ValueError('Jar, driver, or fixture changed during this suite')
                    rows.append({**row,'versionLabel':label,'pairRepetition':repeat,'fullpack':a.fullpack})
                manifest.write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
                print(json.dumps({'event':'record','version':label,'counts':pending,'repeat':repeat,'metrics':rows[-1]['metrics']}),flush=True)
    result=acceptance(rows); result['formal']=a.warm==60 and a.sample==300 and a.repeats==3
    result['settings']={'width':1280,'height':720,'viewDistance':6,'vsync':False,'fpsUncapped':True,'shader':'ComplementaryReimagined_r5.9'}
    (a.output/'summary.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8'); print(json.dumps(result),flush=True)
if __name__=='__main__': main()
