"""Gate stable packaging on evidence for the exact client jar, without conflating long benchmarks."""
import json
from pathlib import Path


def validate(evidence: Path, candidate_hash: str):
    summary = json.loads((evidence / 'summary.json').read_text(encoding='utf-8'))
    if summary['jarSha256'] != candidate_hash or summary['version'] != '0.2.1':
        raise ValueError('Stable evidence does not identify this candidate')
    unit = summary['unitTests']
    if unit['tests'] < 25 or unit['failures'] or unit['errors'] or unit['skipped']:
        raise ValueError('Stable unit checks incomplete')
    gl = json.loads((evidence / 'gl-results.json').read_text(encoding='utf-8'))
    if gl['glError'] or any(gl.get(key) != 'pass' for key in ('gpuTextureCopyAndLazyReadback', 'computeVanillaAndIris54ByteOutput', 'threeIndependentAnimatedInstances', 'batchBindingsRestoredAfterException')):
        raise ValueError('OpenGL checks failed')
    runtime = json.loads((evidence / 'runtime/result.json').read_text(encoding='utf-8'))
    if not runtime['pass'] or runtime['cases'] < 19 or any(runtime[x] for x in ('textureBytesAfterClear', 'meshBytesAfterClear', 'animationBytesAfterClear')):
        raise ValueError('Stable functional checks incomplete')
    suite = json.loads((evidence / 'benchmark/suite.json').read_text(encoding='utf-8'))
    if not suite['complete'] or not suite['pass'] or suite['repetitions'] != 1 or len(suite['results']) != 2:
        raise ValueError('Requested bounded paired suite incomplete')
    for result in suite['results']:
        if not result['result']['pass'] or len(result['comparisons']) != 3 or len(result['rows']) != 6:
            raise ValueError('Missing scene evidence')
        for row in result['rows']:
            if row['jarSha256'] != candidate_hash or row['metrics']['capturedSeconds'] < 59.4:
                raise ValueError('Incomplete or different candidate sample')
        if any(not pair['pass'] for pair in result['comparisons']):
            raise ValueError('Average FPS / 1% Low protection line failed')
    if not summary['pass'] or not summary['visualReview']['pass'] or summary['dsroErrors']:
        raise ValueError('Visual or DSRO error checks failed')
    return summary
