"""Fail closed on incomplete comparisons, unresolved incidents, or missing visual review."""
import argparse, hashlib, json, pathlib
from run_low_suite import acceptance, read

SCENARIOS = {'fixed': (1, 4, 12), 'appearance': (12,), 'flight-new': (12,), 'flight-preloaded': (12,)}

def validate(root, candidate_hash):
    state = read(root / 'matrix.json')
    if not state.get('complete') or not state.get('performancePass') or state['identity']['candidate'] != candidate_hash:
        raise ValueError('Incomplete, failed, or different candidate matrix')
    for scenario, counts in SCENARIOS.items():
        rows = read(root / scenario / 'runs.json')
        expected = {(count, repeat, version) for count in counts for repeat in (1, 2, 3) for version in ('alpha.2', 'alpha.3')}
        actual = [(r['dragonPlayers'], r['pairRepetition'], r['versionLabel']) for r in rows]
        if len(actual) != len(expected) or set(actual) != expected:
            raise ValueError('Incomplete or duplicate pairs: ' + scenario)
        summary = read(root / scenario / 'summary.json')
        if not summary['formal'] or not acceptance(rows)['pass']:
            raise ValueError('Performance acceptance failed: ' + scenario)
        for row in rows:
            expected_hash = state['identity']['baseline' if row['versionLabel'] == 'alpha.2' else 'candidate']
            if row['jarSha256'] != expected_hash or row['driverSha256'] != state['identity']['driver']:
                raise ValueError('Inputs changed: ' + scenario)
            if row['warmupSeconds'] != 60 or row['samplingSeconds'] != 300 or row['metrics']['capturedSeconds'] < 297:
                raise ValueError('Incomplete capture: ' + scenario)
            totals = row['stats']['totals']
            if row['stats']['fallbacks'] or totals.get('READBACK', 0) or not totals.get('GPU_PASS', 0):
                raise ValueError('GPU/texture regression: ' + scenario)
            if scenario == 'fixed' and totals.get('GENERATED', 0):
                raise ValueError('Steady textures regenerated')
            raw = root / row['archivedRawFrames']
            if hashlib.sha256(raw.read_bytes()).hexdigest() != row['archivedRawSha256']:
                raise ValueError('Archived capture changed')
    review = read(root / 'visual-review.json')
    if not review.get('pass') or review['candidateSha256'] != candidate_hash or review.get('shaderPackCount') != 8 or review.get('modelPoseCases') != 30:
        raise ValueError('Missing visual review for this candidate')
    stress = read(root / 'functional/lifecycle-stress/probe-result.json')
    if not stress['pass'] or stress['afterClearTextureBytes'] or stress['afterClearMeshBytes']:
        raise ValueError('Lifecycle stress failed')
    lifecycle = read(root / 'functional/lifecycle-stress/lifecycle-stress.json')
    disconnected = [r for r in lifecycle if r['stage'] == 'disconnected']
    if len(lifecycle) != 16 or len(disconnected) != 4 or any(r['textureBytes'] or r['meshAndPoseBytes'] for r in disconnected):
        raise ValueError('Incomplete disconnect/reconnect cleanup evidence')
    for path in (root / 'incidents').rglob('incident.json'):
        incident = read(path)
        if not incident.get('resolved') or not incident.get('resolutionEvidence'):
            raise ValueError('Unresolved runtime incident: ' + str(path))
    return {'pass': True, 'candidateSha256': candidate_hash, 'pairedRows': 36, 'visualReview': True, 'lifecycleStress': True}

def main():
    p = argparse.ArgumentParser(); p.add_argument('evidence', type=pathlib.Path); p.add_argument('--candidate', required=True, type=pathlib.Path)
    a = p.parse_args()
    result = validate(a.evidence, hashlib.sha256(a.candidate.read_bytes()).hexdigest())
    (a.evidence / 'release-acceptance.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result))

if __name__ == '__main__': main()
