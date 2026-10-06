"""Reproducible serial release validation; never installs or publishes a candidate."""
import argparse, json, pathlib, subprocess, sys, time
from run_low_suite import project, launch, alive, read, file_hash

def functional(arguments):
    result=subprocess.run([sys.executable,str(project/'tools/launch_probe.py'),*arguments],cwd=project,capture_output=True,text=True,encoding='utf-8')
    if result.returncode: raise RuntimeError(result.stderr or result.stdout)
    record=json.loads(result.stdout); root=pathlib.Path(record['instance']); deadline=time.time()+900
    print(json.dumps({'event':'functional','instance':str(root),'pid':record['pid']}),flush=True)
    while time.time()<deadline:
        path=root/'probe-result.json'
        if path.exists():
            evidence=read(path)
            if not evidence['pass']: raise RuntimeError(evidence)
            end=time.time()+120
            while alive(record['pid']) and time.time()<end: time.sleep(1)
            if alive(record['pid']): raise RuntimeError('Our functional client failed to exit')
            return root
        if not alive(record['pid']): raise RuntimeError('Functional probe exited without result: '+str(root))
        time.sleep(2)
    raise TimeoutError('Functional probe timed out: '+str(root))

def main():
    p=argparse.ArgumentParser(); p.add_argument('--baseline',required=True,type=pathlib.Path); p.add_argument('--candidate',required=True,type=pathlib.Path)
    p.add_argument('--pack',required=True,type=pathlib.Path); p.add_argument('--output',required=True,type=pathlib.Path)
    a=p.parse_args(); a.output.mkdir(parents=True,exist_ok=True)
    identity={'baseline':file_hash(a.baseline),'candidate':file_hash(a.candidate),'driver':file_hash(pathlib.Path(__import__('os').environ['LOCALAPPDATA'])/'Temp/DSBR-build/validation/dsbr-render-validation.jar'),
              'fixture':file_hash(a.pack/'fixture-provenance.json'),'launcher':file_hash(project/'tools/launch_probe.py')}
    checkpoint=a.output/'matrix.json'; state=read(checkpoint) if checkpoint.exists() else {'identity':identity,'functional':{},'worlds':{},'suites':{}}
    if state['identity']!=identity: raise ValueError('Matrix inputs changed; use a new output directory')
    def save(): checkpoint.write_text(json.dumps(state,ensure_ascii=False,indent=2),encoding='utf-8')
    common=['--optimizer-jar',str(a.candidate),'--pack',str(a.pack)]
    for name,arguments in [('visual-scenes',['--iris','--shaderpacks','--fullpack','--heap','8G','--visual-scenes']),
                           ('no-shader',['--iris']),('missing-dependencies',['--compat-only'])]:
        if name in state['functional']: continue
        root=functional(common+arguments)
        if name=='visual-scenes' and len(read(root/'visual-scenes.json'))!=30: raise ValueError('Incomplete model/pose matrix')
        state['functional'][name]=str(root); save()
    for scenario in ('flight-new','flight-preloaded'):
        if scenario in state['worlds']: continue
        root=launch(common+['--iris','--shaderpacks','--fullpack','--heap','8G','--multi-dragon-benchmark','--gpu-only','--counts','12',
                            '--scenario',scenario,'--prepare-world','--sample','300'])
        world=root/'saves'/read(root/'multi-result.json')['worldName']
        if not (world/'level.dat').exists(): raise ValueError('Prepared world was not saved')
        state['worlds'][scenario]=str(world); save()
    for scenario,extra in [('fixed',['--group-counts']),('appearance',['--fullpack','--counts','12']),
                           ('flight-new',['--fullpack','--counts','12']),('flight-preloaded',['--fullpack','--counts','12'])]:
        if scenario in state['suites']: continue
        destination=a.output/scenario
        args=[sys.executable,str(project/'tools/run_low_suite.py'),'--baseline',str(a.baseline),'--candidate',str(a.candidate),
              '--pack',str(a.pack),'--output',str(destination),'--scenario',scenario,*extra]
        if scenario in state['worlds']: args+=['--world-template',state['worlds'][scenario]]
        subprocess.run(args,cwd=project,check=True)
        result=read(destination/'summary.json')
        if not result['formal'] or not result['complete']: raise ValueError('Incomplete formal suite: '+scenario)
        state['suites'][scenario]=result; save()
    state['complete']=set(state['suites'])=={'fixed','appearance','flight-new','flight-preloaded'}
    state['performancePass']=state['complete'] and all(s['pass'] for s in state['suites'].values())
    state['releaseApproved']=False  # Human inspection of saved visual evidence is still necessary.
    save(); print(json.dumps(state,ensure_ascii=False),flush=True)
    if not state['performancePass']: sys.exit(2)

if __name__=='__main__': main()
