"""Gate stable packaging on evidence for the exact client jar, without conflating long benchmarks."""
import json
import xml.etree.ElementTree as ET
from pathlib import Path

GL_CHECKS = ('gpuTextureCopyAndLazyReadback', 'computeVanillaAndIris54ByteOutput', 'threeIndependentAnimatedInstances', 'batchBindingsRestoredAfterException')


def gl_checks(evidence: Path):
    """The OpenGL integration check written by `gradlew glCheck` into validation/<version>."""
    path = evidence / 'gl-results.json'
    if not path.exists():
        raise ValueError('Missing OpenGL check: run gradlew glCheck for this version (' + str(path) + ')')
    gl = json.loads(path.read_text(encoding='utf-8'))
    if gl['glError'] or any(gl.get(key) != 'pass' for key in GL_CHECKS):
        raise ValueError('OpenGL checks failed')
    return gl


def unit_tests(build: Path):
    """JUnit results of the build that produced the candidate jar."""
    suites = sorted((build / 'test-results/test').glob('*.xml'))
    if not suites:
        raise ValueError('Missing unit test results in ' + str(build))
    totals = {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0}
    for file in suites:
        suite = ET.parse(file).getroot()
        for key in totals:
            totals[key] += int(suite.attrib.get(key, 0))
    if totals['tests'] < 25 or totals['failures'] or totals['errors'] or totals['skipped']:
        raise ValueError('Unit checks incomplete: ' + json.dumps(totals))
    return totals


def validate(evidence: Path, candidate_hash: str, version: str):
    """Full stable acceptance: functional runtime checks, paired suite and visual review for this exact jar."""
    summary_path = evidence / 'summary.json'
    if not summary_path.exists():
        raise ValueError('No stable acceptance evidence for ' + version + ' (' + str(summary_path) + '); use --without-runtime-evidence to package a build with basic checks only')
    summary = json.loads(summary_path.read_text(encoding='utf-8'))
    if summary['jarSha256'] != candidate_hash or summary['version'] != version:
        raise ValueError('Stable evidence does not identify this candidate')
    unit = summary['unitTests']
    if unit['tests'] < 25 or unit['failures'] or unit['errors'] or unit['skipped']:
        raise ValueError('Stable unit checks incomplete')
    gl_checks(evidence)
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
