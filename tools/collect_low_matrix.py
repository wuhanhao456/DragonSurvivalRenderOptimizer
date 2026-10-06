"""Archive inspectable results and compressed full captures; never copy worlds or accounts."""
import argparse, gzip, hashlib, json, pathlib, shutil
from run_low_suite import read, file_hash, acceptance

def main():
    p=argparse.ArgumentParser(); p.add_argument('matrix',type=pathlib.Path); p.add_argument('--output',required=True,type=pathlib.Path)
    p.add_argument('--eight-shaders',required=True,type=pathlib.Path); p.add_argument('--partial',action='store_true'); a=p.parse_args()
    state=read(a.matrix/'matrix.json')
    if not state.get('complete') and not a.partial: raise ValueError('Formal matrix is incomplete; --partial preserves unfinished evidence without claiming acceptance')
    out=a.output; out.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(a.matrix/'hardware.json',out/'hardware.json')
    archived={}; all_rows=[]
    for scenario in ('fixed','appearance','flight-new','flight-preloaded'):
        source=a.matrix/scenario
        if a.partial and not (source/'runs.json').exists(): continue
        rows=read(source/'runs.json')
        expected=18 if scenario=='fixed' else 6
        if not a.partial and (len(rows)!=expected or not acceptance(rows)['complete']): raise ValueError('Missing paired rows: '+scenario)
        destination=out/scenario; destination.mkdir(exist_ok=True)
        shutil.copyfile(source/'inputs.json',destination/'inputs.json')
        if (source/'summary.json').exists(): shutil.copyfile(source/'summary.json',destination/'summary.json')
        else: (destination/'summary.json').write_text(json.dumps({**acceptance(rows),'formal':False,'stoppedByUser':True},indent=2),encoding='utf-8')
        for row in rows:
            root=pathlib.Path(row['instance']); raw=root/row['rawFrames']
            if file_hash(raw)!=row['rawSha256']: raise ValueError('Raw capture changed')
            result=read(root/'multi-result.json')
            if not result['pass'] or result['meshBytesAfterClear'] or result['textureBytesAfterClear']: raise ValueError('Cleanup failed')
            name=f"p{row['dragonPlayers']}-r{row['pairRepetition']}-{row['versionLabel']}"
            folder=destination/name; folder.mkdir(exist_ok=True)
            with raw.open('rb') as input, (folder/'frames.csv.gz').open('wb') as output:
                with gzip.GzipFile(filename='',mode='wb',fileobj=output,mtime=0) as compressed: shutil.copyfileobj(input,compressed)
            shutil.copyfile(root/'multi-result.json',folder/'cleanup.json')
            screenshot=root/f"players-{row['dragonPlayers']}.png"
            if screenshot.exists(): shutil.copyfile(screenshot,folder/'players.png')
            row['archivedRawFrames']=f'{scenario}/{name}/frames.csv.gz'; row['archivedRawSha256']=file_hash(folder/'frames.csv.gz')
            all_rows.append(row)
        (destination/'runs.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
    functional={**state['functional'],'eight-shaders':str(a.eight_shaders)}
    for name,instance in functional.items():
        root=pathlib.Path(instance)
        if file_hash(root/'mods/optimizer.jar')!=state['identity']['candidate']: raise ValueError('Functional jar differs: '+name)
        result=read(root/'probe-result.json')
        if not result['pass']: raise ValueError('Functional probe failed: '+name)
        folder=out/'functional'/name; folder.mkdir(parents=True,exist_ok=True)
        for pattern in ('*.json','*.png'):
            for file in root.glob(pattern):
                if file.suffix=='.png' or file.name in {'probe-result.json','visual-scenes.json','after-reload.json','baseline-textures.json','changed-armor.json','changed-skin.json','equivalent-state.json','gpu-mode.json','restored-skin.json','default-gpu.json'} or file.name.startswith(('inventory-','shader-')):
                    shutil.copyfile(file,folder/file.name)
        archived[name]={'clientSha256':file_hash(root/'mods/optimizer.jar'),'driverSha256':file_hash(root/'mods/validation.jar'),'pass':True}
    state['functionalEvidence']=archived; state['archivedRows']=len(all_rows)
    (out/'matrix.json').write_text(json.dumps(state,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'rows':len(all_rows),'performancePass':state.get('performancePass'),'functional':list(archived),'partial':a.partial}))

if __name__=='__main__': main()
