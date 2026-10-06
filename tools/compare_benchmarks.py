"""Summarize exported 60/300 second runs; refuse to call fallback-only runs GPU acceptance."""
import argparse, json, pathlib

p = argparse.ArgumentParser(); p.add_argument('directory', type=pathlib.Path); p.add_argument('--output', type=pathlib.Path, default=pathlib.Path('benchmark-summary.json')); a = p.parse_args()
rows = []
for run in sorted(a.directory.rglob('comparison.json')):
    records = json.loads(run.read_text(encoding='utf-8'))
    if len(records) != 3: raise ValueError('Expected exactly three mode records: ' + str(run))
    modes = {x['mode']: x for x in records}
    if set(modes) != {'VANILLA', 'TEXTURES', 'GPU'}: raise ValueError('Incomplete mode set: ' + str(run))
    for record in records:
        if record.get('warmupSeconds') != 60 or record.get('samplingSeconds') != 300: raise ValueError('Wrong measurement duration: ' + str(run))
    for key in ('sceneDragonPlayers', 'repetition', 'width', 'height', 'renderDistance', 'fpsLimit', 'gpu', 'vsync', 'shaderPack', 'shaderEnabled'):
        if any(x.get(key) != records[0].get(key) for x in records): raise ValueError('Scene metadata changed: ' + key + ' in ' + str(run))
    row = {'source': str(run), 'players': modes['GPU']['sceneDragonPlayers'], 'repetition': modes['GPU']['repetition']}
    for mode, record in modes.items():
        stats = record['stats']; totals = stats['totals']; n = stats['frameSamples']
        if n == 0: raise ValueError('No frames: ' + str(run))
        row[mode] = {'frames': n, **stats['frameTimeMs'], 'generated': totals.get('GENERATED', 0), 'readbacks': totals.get('READBACK', 0),
                     'vertexSubmitMsPerFrame': (totals.get('CPU_VERTEX_SUBMIT_NANOS', 0) + totals.get('GPU_BONE_SUBMIT_NANOS', 0)) / n / 1e6,
                     'gpuDrawCpuMsPerFrame': totals.get('GPU_DRAW_CPU_NANOS', 0) / n / 1e6,
                     'textureBytes': record['textureBytes'], 'meshBytes': record['meshBytes'], 'resourcePeaks': stats.get('peaks', {}),
                     'cpuFallbacks': totals.get('CPU_FALLBACK', 0), 'fallbacks': stats['fallbacks']}
    baseline, gpu = row['TEXTURES'], row['GPU']; submit = baseline['vertexSubmitMsPerFrame']
    row['vertexSubmissionReduction'] = 1 - gpu['vertexSubmitMsPerFrame'] / submit if submit > 0 else None
    row['p95RatioAgainstTextures'] = gpu['p95'] / baseline['p95']
    row['gpuPathExercised'] = modes['GPU']['stats']['totals'].get('GPU_PASS', 0) > 0
    faults = {k: v for k, v in gpu['fallbacks'].items() if k != 'benchmark cancelled'}
    row['criteriaPass'] = row['gpuPathExercised'] and not faults and gpu['readbacks'] == 0 and baseline['readbacks'] == 0 and baseline['generated'] == 0 and gpu['generated'] == 0 and row['vertexSubmissionReduction'] is not None and row['vertexSubmissionReduction'] >= .5 and row['p95RatioAgainstTextures'] <= 1.05
    rows.append(row)
groups = {(x['players'], x['repetition']) for x in rows}; expected = {(x, y) for x in (1, 4, 12) for y in (1, 2, 3)}
result = {'completeMatrix': groups == expected, 'allCriteriaPass': groups == expected and all(x['criteriaPass'] for x in rows), 'runs': rows}
a.output.parent.mkdir(parents=True, exist_ok=True); a.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'completeMatrix': result['completeMatrix'], 'allCriteriaPass': result['allCriteriaPass'], 'runs': len(rows)}))
