"""Archive the bounded render-only short benchmark; do not label it formal acceptance."""
import argparse, hashlib, json, math, pathlib, shutil, statistics

project = pathlib.Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(); p.add_argument('--instance', required=True, type=pathlib.Path); a = p.parse_args()
read = lambda path: json.loads(path.read_text(encoding='utf-8'))
result = read(a.instance / 'multi-result.json'); rows = read(a.instance / 'multi-comparison.json')
assert result['pass'] and result['rows'] == len(rows) == 18, 'Incomplete or failed matrix'
assert result['textureBytesAfterClear'] == result['meshBytesAfterClear'] == 0
out = project / 'validation/multi-dragon-iris'; out.mkdir(exist_ok=True)
points = [tuple(float(v) for v in r['camera'].strip('()').split(',')) for r in rows]
assert all(max(p[i] for p in points) - min(p[i] for p in points) < .1 for i in range(3)), 'Camera moved'
groups = []
for count in (1, 4, 12):
    for mode in ('VANILLA', 'TEXTURES', 'GPU'):
        group = [r for r in rows if r['dragonPlayers'] == count and r['mode'] == mode]
        assert len(group) == 2 and {r['repetition'] for r in group} == {1, 2}
        for row in group:
            assert row['samplingSeconds'] >= 14.9 and row['warmupSeconds'] == 5
            assert (row['width'], row['height'], row['renderDistance'], row['fpsLimit']) == (1280, 720, 6, 260)
            assert len(row['actorScales']) == count and max(row['actorScales']) - min(row['actorScales']) < 1e-5
            assert len(set(row['actorStages'])) == 1 and max(row['actorGrowth']) - min(row['actorGrowth']) < .01
            assert not row['stats']['fallbacks']
            if mode == 'GPU':
                stats = row['stats']; assert stats['attribution']['entity']['GPU_PASS'] / stats['frameSamples'] >= count * 1.8
            if mode != 'VANILLA': assert row['stats']['totals'].get('READBACK', 0) == 0
        frames = sum(r['stats']['frameSamples'] for r in group)
        def stage_ms(stage): return sum(r['stats']['totals'].get(stage + '_NANOS', 0) for r in group) / frames / 1e6
        groups.append({'dragonPlayers': count, 'mode': mode, 'averageFps': frames / sum(r['samplingSeconds'] for r in group),
                       'meanRunP50Ms': statistics.mean(r['stats']['frameTimeMs']['p50'] for r in group),
                       'meanRunP95Ms': statistics.mean(r['stats']['frameTimeMs']['p95'] for r in group),
                       'meanRunP99Ms': statistics.mean(r['stats']['frameTimeMs']['p99'] for r in group),
                       'cpuFrameMsPerFrame': stage_ms('FRAME_CPU'), 'cpuVertexSubmitMsPerFrame': stage_ms('CPU_VERTEX_SUBMIT'),
                       'gpuPoseCaptureMsPerFrame': stage_ms('GPU_POSE_CAPTURE'), 'gpuDrawCpuMsPerFrame': stage_ms('GPU_DRAW_CPU'),
                       'generated': sum(r['stats']['totals'].get('GENERATED', 0) for r in group),
                       'readbacks': sum(r['stats']['totals'].get('READBACK', 0) for r in group),
                       'peakTextureBytes': max(r['stats'].get('peaks', {}).get('TEXTURE_RESOURCE_BYTES', 0) for r in group),
                       'peakGpuResourceBytes': max(r['stats'].get('peaks', {}).get('GPU_RESOURCE_BYTES', 0) for r in group)})
for name in ('multi-result.json', 'multi-comparison.json', 'players-1.png', 'players-4.png', 'players-12.png'): shutil.copyfile(a.instance / name, out / name)
summary = {'date': '2026-10-06', 'testedJarSha256': hashlib.sha256((a.instance / 'mods/optimizer.jar').read_bytes()).hexdigest(),
           'hardware': {'cpu': 'Intel Core i7-13700K', 'gpu': 'NVIDIA RTX 5070 Ti', 'driver': '596.49'},
           'shader': 'ComplementaryReimagined_r5.9', 'renderOnlySyntheticPlayers': True, 'networkLoadMeasured': False,
           'warmupSeconds': 5, 'sampleSecondsPerRun': 15, 'repetitions': 2, 'secondRunModeOrderReversed': True,
           'resolution': '1280x720', 'renderDistance': 6, 'vsync': False, 'fpsUncapped': True,
           'quantileMethod': 'Average of two run quantiles, not pooled quantiles',
           'groups': groups, 'cleanupBytes': 0, 'formalPerformanceMatrixCompleted': False, 'fullModpackVisualRegressionCompleted': False}
(out / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(groups, ensure_ascii=False, indent=2))
