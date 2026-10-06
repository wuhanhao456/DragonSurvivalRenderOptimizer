"""Allocation samples and GC pause overlap within the recorded frame window only."""
import argparse, collections, datetime, json, pathlib, re, subprocess
from low_metrics import metrics

def seconds(iso): return datetime.datetime.fromisoformat(iso.replace('Z','+00:00')).timestamp()
def duration(iso):
    parts=re.fullmatch(r'PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?',iso).groups()
    return sum(float(v or 0)*scale for v,scale in zip(parts,(3600,60,1)))

def main():
    p=argparse.ArgumentParser(); p.add_argument('--instance',type=pathlib.Path,required=True); p.add_argument('--jfr-tool',type=pathlib.Path,default=pathlib.Path('C:/Program Files/Java/jdk-21/bin/jfr.exe')); a=p.parse_args()
    row=json.loads((a.instance/'multi-comparison.json').read_text())[0]
    start=seconds(row['frameTimelineOriginUtc']); window=metrics(a.instance/row['rawFrames'])['capturedSeconds']; end=start+window
    data=a.instance/'jfr-events.json'
    with data.open('w',encoding='utf-8') as output:
        subprocess.run([str(a.jfr_tool),'print','--json','--stack-depth','8','--events','jdk.ObjectAllocationSample,jdk.GCPhasePause',str(a.instance/'diagnostic.jfr')],stdout=output,check=True)
    events=json.loads(data.read_text())['recording']['events']; classes=collections.Counter(); callsites=collections.Counter(); pauses=[]
    for event in events:
        v=event['values']; t=seconds(v['startTime'])
        if event['type']=='jdk.ObjectAllocationSample' and start<=t<=end:
            thread=v.get('eventThread') or {}; name=thread.get('javaName','')
            # The launch thread is renamed by Minecraft. JFR may preserve its
            # original name ("main") for the entire recording's thread object.
            if name!='Render thread' and thread.get('javaThreadId')!=1: continue
            weight=v.get('weight',0); classes[v['objectClass']['name']]+=weight
            trace=v.get('stackTrace') or {}; frames=trace.get('frames',[])
            if frames:
                method=frames[0]['method']; callsites[method['type']['name']+'.'+method['name']]+=weight
        elif event['type']=='jdk.GCPhasePause':
            length=duration(v['duration']); overlap=max(0,min(end,t+length)-max(start,t))
            if overlap: pauses.append({'relativeStartSeconds':t-start,'durationMs':length*1000,'overlapMs':overlap*1000})
    report={'sampleWindowSeconds':window,'renderAllocationSampleWeightedBytes':sum(classes.values()),'estimatedRenderAllocationMiBPerSecond':sum(classes.values())/window/1048576,
            'topSampledClasses':classes.most_common(15),'topSampledCallsites':callsites.most_common(15),'gcPauses':pauses,'gcPauseOverlapMs':sum(p['overlapMs'] for p in pauses),
            'method':'JFR ObjectAllocationSample weights are estimates; pauses overlap the sampled frame timeline, not proof of sole causation; exclude profiler runs from performance acceptance.'}
    (a.instance/'jfr-analysis.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k!='gcPauses'},indent=2))
if __name__=='__main__': main()
