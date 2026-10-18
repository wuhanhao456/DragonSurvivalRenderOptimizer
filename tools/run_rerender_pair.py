"""Paired default-GPU short test: stage 4 (no re-render pose reuse) against stage 5 (release default), same jar and scenes.

Optional --verify adds one stage-5 run that still traverses reused re-renders and counts byte mismatches against the reused bones and changes to GeckoLib's tracked bone matrices.
"""
import argparse, hashlib, json, pathlib, subprocess, sys, time
from low_metrics import metrics
from run_low_suite import alive
from artifact_name import artifact_name

PROJECT = pathlib.Path(__file__).resolve().parents[1]
SCENES = 'players-1-off,players-12-off,souls17-npc8-off'
SHADER = 'ComplementaryUnbound_r5.9.zip'


def read(p): return json.loads(p.read_text(encoding='utf-8'))
def digest(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p, d): p.parent.mkdir(parents=True, exist_ok=True); p.write_text(json.dumps(d, ensure_ascii=False, indent=2), encoding='utf-8')


def run(a, shader, stage, verify=False):
    flags = ['--build', str(a.build), '--pack', str(a.pack), '--fullpack', '--iris', '--low-end-scenes', a.scenes,
             '--warm', str(a.warm), '--sample', str(a.sample), '--heap', '8G', '--stage', str(stage)]
    if shader: flags += ['--shaderpacks', '--shader', SHADER]
    if verify: flags += ['--verify-rerender']
    process = subprocess.run([sys.executable, str(PROJECT / 'tools/launch_probe.py'), *flags], cwd=PROJECT, capture_output=True, text=True, encoding='utf-8')
    if process.returncode: raise RuntimeError(process.stderr)
    launch = json.loads(process.stdout); root = pathlib.Path(launch['instance'])
    print(json.dumps({'event': 'started', 'shader': shader, 'stage': stage, 'verify': verify, 'instance': str(root)}), flush=True)
    deadline = time.time() + 1500
    while not (root / 'lowend-result.json').exists():
        if not alive(launch['pid']): raise RuntimeError('Probe exited without result: ' + str(root))
        if time.time() > deadline: raise TimeoutError('Run exceeded 25 minutes: ' + str(root))
        time.sleep(2)
    result = read(root / 'lowend-result.json')
    if not result['pass']: raise RuntimeError(result)
    assert result['meshBytesAfterClear'] == result['textureBytesAfterClear'] == result['animationBytesAfterClear'] == 0
    jar = a.build / 'libs' / (artifact_name(PROJECT) + '.jar'); assert digest(jar) == digest(root / 'mods/optimizer.jar')
    rows = []
    for row in read(root / 'lowend-scenes.json'):
        t = row['stats']['totals']; assert not t.get('READBACK', 0) and not row['lowEnd']
        assert (row['width'], row['height'], row['renderDistance'], row['swapInterval']) == (1280, 720, 6, 0) and not row['vsync']
        if shader: assert row['shaderPack'] == SHADER
        m = metrics(root / row['rawFrames']); assert m['capturedSeconds'] >= a.sample - 0.6
        frames = m['samples']
        rows.append({'scene': row['scene'], 'metrics': m, 'fallbacks': row['stats']['fallbacks'],
                     'perFrameMs': {k[:-6]: t.get(k, 0) / frames / 1e6 for k in ('FRAME_CPU_NANOS', 'GPU_POSE_CAPTURE_NANOS', 'GPU_BATCH_CPU_NANOS', 'SOUL_RENDER_CPU_NANOS')},
                     'passesPerFrame': t.get('GPU_PASS', 0) / frames, 'drawsPerFrame': t.get('GPU_DRAW_CALLS', 0) / frames,
                     'reused': t.get('RERENDER_POSE_REUSED', 0), 'missed': t.get('RERENDER_POSE_MISS', 0),
                     'verified': t.get('RERENDER_POSE_VERIFIED', 0), 'mismatched': t.get('RERENDER_POSE_MISMATCH', 0),
                     'trackedVerified': t.get('RERENDER_TRACKED_VERIFIED', 0), 'trackedMismatched': t.get('RERENDER_TRACKED_MISMATCH', 0),
                     'cpuFallbacks': t.get('CPU_FALLBACK', 0), 'rawSha256': digest(root / row['rawFrames'])})
    stop = time.time() + 120
    while alive(launch['pid']) and time.time() < stop: time.sleep(1)
    return {'shader': shader, 'stage': stage, 'verify': verify, 'jarSha256': digest(jar), 'instance': str(root), 'rows': rows}


def compare(base, cand):
    change = lambda x, y: (x / y - 1) * 100
    out = []
    for b in base['rows']:
        c = next(r for r in cand['rows'] if r['scene'] == b['scene'])
        pair = {'scene': b['scene'], 'shader': base['shader'],
                'averageFps': [b['metrics']['averageFps'], c['metrics']['averageFps']], 'averageFpsChangePercent': change(c['metrics']['averageFps'], b['metrics']['averageFps']),
                'low1Fps': [b['metrics']['low1Fps'], c['metrics']['low1Fps']], 'low1ChangePercent': change(c['metrics']['low1Fps'], b['metrics']['low1Fps']),
                'poseCaptureMsPerFrame': [b['perFrameMs']['GPU_POSE_CAPTURE'], c['perFrameMs']['GPU_POSE_CAPTURE']],
                'frameCpuMsPerFrame': [b['perFrameMs']['FRAME_CPU'], c['perFrameMs']['FRAME_CPU']],
                'candidateReused': c['reused'], 'candidateMissed': c['missed'], 'baselineReused': b['reused']}
        pair['pass'] = pair['averageFpsChangePercent'] >= -5 and pair['low1ChangePercent'] >= -5 and b['reused'] == 0
        out.append(pair)
    return out


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--build', type=pathlib.Path, required=True); p.add_argument('--pack', type=pathlib.Path, required=True)
    p.add_argument('--output', type=pathlib.Path, required=True); p.add_argument('--shaders', default='plain,shader')
    p.add_argument('--scenes', default=SCENES); p.add_argument('--warm', type=int, default=15); p.add_argument('--sample', type=int, default=60)
    p.add_argument('--verify', action='store_true'); a = p.parse_args(); a.output.mkdir(parents=True, exist_ok=True)
    suite = {'scenes': a.scenes, 'warmupSeconds': a.warm, 'sampleSeconds': a.sample, 'repetitions': 1, 'comparisons': [], 'runs': []}
    # Correctness first: a mismatch makes the performance pairs pointless.
    if a.verify:
        check = run(a, False, 5, verify=True); write(a.output / 'verify.json', check)
        suite['verify'] = {'reused': sum(r['reused'] for r in check['rows']), 'verified': sum(r['verified'] for r in check['rows']),
                           'mismatched': sum(r['mismatched'] for r in check['rows']),
                           'trackedVerified': sum(r['trackedVerified'] for r in check['rows']), 'trackedMismatched': sum(r['trackedMismatched'] for r in check['rows'])}
        suite['verify']['pass'] = suite['verify']['verified'] > 0 and suite['verify']['mismatched'] == 0 and suite['verify']['trackedMismatched'] == 0
        write(a.output / 'suite.json', suite); print(json.dumps({'event': 'verified', **suite['verify']}), flush=True)
        if not suite['verify']['pass']: sys.exit(1)
    for shader in [s == 'shader' for s in a.shaders.split(',')]:
        name = 'shader' if shader else 'plain'
        base = run(a, shader, 4); write(a.output / f'{name}-stage4.json', base)
        cand = run(a, shader, 5); write(a.output / f'{name}-stage5.json', cand)
        suite['runs'] += [base['instance'], cand['instance']]; suite['comparisons'] += compare(base, cand)
        write(a.output / 'suite.json', suite); print(json.dumps({'event': 'compared', 'shader': name, 'comparisons': compare(base, cand)}), flush=True)
    suite['pass'] = all(c['pass'] for c in suite['comparisons']) and suite.get('verify', {}).get('pass', True)
    write(a.output / 'suite.json', suite); print(json.dumps({'event': 'done', 'pass': suite['pass']}), flush=True)
