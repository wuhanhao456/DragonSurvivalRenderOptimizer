"""Collect successful probes and bounded evidence, excluding worlds/accounts/dependency jars."""
import argparse, hashlib, json, pathlib, shutil, subprocess, sys, xml.etree.ElementTree as ET

project = pathlib.Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(); p.add_argument('--runtime', required=True, type=pathlib.Path); p.add_argument('--fallback', required=True, type=pathlib.Path); p.add_argument('--build', required=True, type=pathlib.Path); a = p.parse_args()
read = lambda path: json.loads(path.read_text(encoding='utf-8'))
digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
runtime = read(a.runtime / 'probe-result.json'); fallback = read(a.fallback / 'probe-result.json')
assert runtime['pass'] and fallback['pass'], 'Cannot archive failed probes as passes'
jar = next((a.build / 'libs').glob('*alpha.1.jar')); tested_hash = digest(a.runtime / 'mods/optimizer.jar')
assert digest(jar) == tested_hash == digest(a.fallback / 'mods/optimizer.jar'), 'Probe jar differs from delivery jar'
assert runtime['afterClearTextureBytes'] == runtime['afterClearMeshBytes'] == 0, 'Resource cleanup failed'
assert runtime['modId'] == 'dsbr' and not runtime['legacyActive'], 'Wrong identity or legacy takeover on DS 2.0.71'
assert runtime['legacyConfigFlag'] and runtime['savedOldRenderMode'] == 'BEDROCK', 'Old config migration was not exercised'
out = project / 'validation'; target = out / 'runtime-iris'; target.mkdir(parents=True, exist_ok=True)
files = ['probe-result.json', 'baseline-textures.json', 'equivalent-state.json', 'changed-skin.json', 'changed-armor.json', 'restored-skin.json', 'gpu-mode.json', 'after-reload.json', 'plain-textures.png']
shaders = []
for i in range(8):
    prefix = f'shader-{i}'; files.extend(prefix + suffix for suffix in ('-name.txt', '-gpu.json', '-textures.png', '-gpu.png'))
    stats = read(a.runtime / (prefix + '-gpu.json')); totals = stats['totals']
    faults = {k: v for k, v in stats['fallbacks'].items() if k != 'benchmark cancelled'}
    assert totals.get('GPU_PASS', 0) > 0 and totals.get('READBACK', 0) == 0 and not faults, 'Shader rendering failed'
    phases = {k: v['GPU_PASS'] for k, v in stats['attribution'].items() if 'GPU_PASS' in v}
    assert phases.get('glow/entity', 0) > 0, 'Glow GPU path missing'
    shaders.append({'name': (a.runtime / (prefix + '-name.txt')).read_text(), 'pass': True, 'gpuPasses': totals['GPU_PASS'], 'phasePasses': phases,
                    'normalTextureReadbacks': totals.get('READBACK', 0), 'cpuFallbacks': totals.get('CPU_FALLBACK', 0), 'resourcePeaks': stats.get('peaks', {})})
for file in files: shutil.copyfile(a.runtime / file, target / file)
shutil.copyfile(a.fallback / 'probe-result.json', out / 'fallback-result.json')
cases = []
for file in sorted((a.build / 'test-results/test').glob('*.xml')):
    suite = ET.parse(file).getroot(); assert suite.attrib.get('failures') == suite.attrib.get('errors') == '0'
    cases.extend({'class': suite.attrib['name'], 'name': test.attrib['name'], 'seconds': float(test.attrib['time']), 'pass': True} for test in suite.findall('testcase'))
tool_tests = subprocess.run([sys.executable, str(project / 'tools/test_benchmark_summary.py')], capture_output=True, text=True)
assert tool_tests.returncode == 0, tool_tests.stderr
(out / 'benchmark-tool-tests.txt').write_text(tool_tests.stderr, encoding='utf-8')
sync = read(target / 'equivalent-state.json'); skin = read(target / 'changed-skin.json'); armor = read(target / 'changed-armor.json'); restored = read(target / 'restored-skin.json')
assert sync['totals']['STATE_SYNC'] >= 100 and sync['totals'].get('GENERATED', 0) == 0
assert skin['totals']['GENERATED'] == armor['totals']['GENERATED'] == 1 and restored['totals'].get('GENERATED', 0) == 0
summary = {'date': '2026-10-06', 'version': '0.2.0-alpha.1', 'testedJarSha256': tested_hash,
           'hardware': {'cpu': 'Intel Core i7-13700K', 'gpu': 'NVIDIA RTX 5070 Ti', 'driver': '596.49'},
           'javaTests': cases, 'benchmarkToolSyntheticTests': {'count': 4, 'pass': True, 'performanceMeasurements': False},
           'openGLTests': read(out / 'gl-results.json'), 'compatibility': runtime['compatibility'], 'optionalDependencyFallback': fallback,
           'upgradeMigration': {'modId': runtime['modId'], 'oldRenderModePreserved': runtime['savedOldRenderMode'], 'legacyConfigFlag': runtime['legacyConfigFlag'], 'legacyActiveOnDS2071': runtime['legacyActive']},
           'cache': {'equivalentSyncs': sync['totals']['STATE_SYNC'], 'equivalentGenerated': 0, 'changedSkinGenerated': 1, 'changedArmorGenerated': 1, 'restoredGenerated': 0},
           'cleanup': {'textureBytes': runtime['afterClearTextureBytes'], 'meshBytes': runtime['afterClearMeshBytes']},
           'shaderSmokeTests': shaders, 'formalPerformanceMatrixCompleted': False, 'fullModpackVisualRegressionCompleted': False, 'releaseAcceptanceComplete': False,
           'method': 'Fresh isolated flat world; DS/Gecko/optimizer/test driver/Iris/Sodium only; 1280x720, view distance 6, FPS cap 120; short functional checks, not 60/300-second benchmarks'}
(out / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'jarSha256': tested_hash, 'javaTests': len(cases), 'shaderPacks': len(shaders), 'cachePass': True, 'cleanupBytes': summary['cleanup'], 'formalAcceptanceComplete': False}))
