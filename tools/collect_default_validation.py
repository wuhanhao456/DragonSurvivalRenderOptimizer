"""Collect fresh-config GPU checks separately from the alpha.1 rendering regression."""
import argparse, hashlib, json, pathlib, shutil, tomllib, xml.etree.ElementTree as ET
from artifact_name import artifact_name

project = pathlib.Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
for name in ('plain', 'iris', 'fallback', 'build'): p.add_argument('--' + name, required=True, type=pathlib.Path)
a = p.parse_args()
read = lambda path: json.loads(path.read_text(encoding='utf-8'))
digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
version = next(x.split('=', 1)[1] for x in (project / 'gradle.properties').read_text(encoding='utf-8').splitlines() if x.startswith('mod_version='))
jar_hash = digest(a.build / 'libs' / (artifact_name(project) + '.jar'))
out = project / 'validation/default-gpu'; out.mkdir(exist_ok=True)
cases = []
for label, directory in (('plain', a.plain), ('iris', a.iris)):
    result = read(directory / 'probe-result.json'); stats = read(directory / 'default-gpu.json')
    assert result['pass'] and result['startupMode'] == 'GPU'
    assert digest(directory / 'mods/optimizer.jar') == jar_hash
    assert tomllib.loads((directory / 'config/dsbr-optimizer-client.toml').read_text(encoding='utf-8'))['mode'] == 'GPU'
    assert stats['totals']['GPU_PASS'] > 0 and stats['totals'].get('READBACK', 0) == 0 and not stats['fallbacks']
    assert result['afterClearTextureBytes'] == result['afterClearMeshBytes'] == 0
    if label == 'iris': assert result['startupShader'] == 'ComplementaryReimagined_r5.9.zip'
    for source, target in (('probe-result.json', label + '-result.json'), ('default-gpu.json', label + '-stats.json')):
        shutil.copyfile(directory / source, out / target)
    previews = {}
    for mode in ('vanilla', 'textures', 'gpu'):
        name = 'inventory-' + mode
        if not (directory / (name + '.json')).is_file(): continue
        gui = read(directory / (name + '.json'))
        assert gui['totals'].get('CPU_VERTEX_SUBMIT_NANOS', 0) > 0
        if mode == 'gpu':
            assert gui['totals'].get('GUI_CPU_PASS', 0) > 0 and gui['totals'].get('GPU_PASS', 0) > 0
            assert gui['totals'].get('READBACK', 0) == 0 and not gui['fallbacks']
        for suffix in ('.json', '.png', '-screen.txt'): shutil.copyfile(directory / (name + suffix), out / (label + '-' + name + suffix))
        previews[mode] = {'pass': True, 'screen': (directory / (name + '-screen.txt')).read_text(), 'guiCpuPasses': gui['totals'].get('GUI_CPU_PASS', 0)}
    cases.append({'scenario': label, 'pass': True, 'startupMode': result['startupMode'], 'shader': result.get('startupShader'),
                  'gpuPasses': stats['totals']['GPU_PASS'], 'normalTextureReadbacks': 0,
                  'phaseCounts': {k: v['GPU_PASS'] for k, v in stats['attribution'].items() if 'GPU_PASS' in v}, 'inventoryPreviews': previews, 'cleanupBytes': 0})
fallback = read(a.fallback / 'probe-result.json')
assert fallback['pass'] and fallback['configuredMode'] == 'GPU' and digest(a.fallback / 'mods/optimizer.jar') == jar_hash
shutil.copyfile(a.fallback / 'probe-result.json', out / 'fallback-result.json')
tests = 0
for path in (a.build / 'test-results/test').glob('*.xml'):
    suite = ET.parse(path).getroot(); assert suite.attrib['failures'] == suite.attrib['errors'] == '0'
    tests += len(suite.findall('testcase'))
assert tests == 10
summary = {'version': version, 'date': '2026-10-06', 'testedJarSha256': jar_hash, 'defaultMode': 'GPU', 'modeCommandsIssued': False,
           'javaTestsPassed': tests, 'freshConfigCases': cases, 'missingDependencyFallback': fallback,
           'baseRenderingRegression': 'validation/summary.json', 'eightShaderRegressionRepeated': True,
           'formalAcceptanceComplete': False}
(out.parent / 'default-gpu-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'version': version, 'jarSha256': jar_hash, 'defaultGpuCasesPassed': len(cases), 'fallbackPassed': True}))
