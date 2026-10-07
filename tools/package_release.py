"""Package only optimizer source/evidence; never include runtime dependencies or user state."""
import argparse, hashlib, json, os, pathlib, shutil, tempfile, zipfile
from artifact_name import artifact_name

project = pathlib.Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(); p.add_argument('--build', type=pathlib.Path, default=pathlib.Path(os.environ['LOCALAPPDATA']) / 'Temp/DSBR-build'); p.add_argument('--incomplete-prerelease',action='store_true',help='Package a clearly labelled test build after the user stops remaining runtime tests'); a = p.parse_args()
dist = project / 'dist'; dist.mkdir(exist_ok=True)
version = next(x.split('=', 1)[1] for x in (project / 'gradle.properties').read_text().splitlines() if x.startswith('mod_version='))
name = artifact_name(project)
formal = False
single_round = None
stable_evidence = None
prerelease = "-" in version
if a.incomplete_prerelease and not prerelease:
    raise ValueError("Incomplete evidence cannot be packaged as a stable release")
if not prerelease:
    if version != "0.2.1": raise ValueError("No stable acceptance contract for this version")
    from stable_release_acceptance import validate
    candidate_hash = hashlib.sha256((a.build / "libs" / (name + ".jar")).read_bytes()).hexdigest()
    stable_evidence = validate(project / "validation/0.2.1", candidate_hash)
if version == '0.2.0-alpha.4':
    candidate_hash = hashlib.sha256((a.build / 'libs' / (name + '.jar')).read_bytes()).hexdigest()
    single_round = json.loads((project / 'validation/alpha4/summary.json').read_text(encoding='utf-8'))
    if single_round['jarSha256'] != candidate_hash or single_round['repetitions'] != 1:
        raise ValueError('Missing single-round evidence for this candidate')
    if not single_round['singleRoundComplete'] or single_round['unitTests']['tests'] == 0 or single_round['unitTests']['failures'] or single_round['unitTests']['errors'] or single_round['openGL']['glError']:
        raise ValueError('Incomplete runtime capture or failed build/OpenGL checks')
    if not single_round.get('startupSmoke', {}).get('pass'):
        raise ValueError('Requested startup/world smoke check has not passed')
if version == '0.2.0-alpha.3':
    candidate_hash = hashlib.sha256((a.build / 'libs' / (name + '.jar')).read_bytes()).hexdigest()
    if a.incomplete_prerelease:
        evidence = json.loads((project / 'validation/low-frames/matrix.json').read_text(encoding='utf-8'))
        if not evidence.get('stoppedByUser') or evidence['identity']['candidate'] != candidate_hash or evidence.get('archivedRows',0) < 6:
            raise ValueError('Missing stopped-run evidence for this candidate')
    else:
        from release_acceptance import validate
        formal = validate(project / 'validation/low-frames', candidate_hash)['pass']
for suffix in ('.jar', '-sources.jar'): shutil.copyfile(a.build / 'libs' / (name + suffix), dist / (name + suffix))
with zipfile.ZipFile(dist / (name + '.jar')) as jar:
    members = jar.namelist()
    assert not any('validation/' in x or x.startswith('by/') or x.endswith(('.dll', '.so')) for x in members), 'Unexpected runtime/test dependency bundled'
    assert 'LICENSE' in members and 'THIRD_PARTY_NOTICES.md' in members and 'dsbr.optimizer.mixins.json' in members and 'dsbr.mixins.json' not in members
allowed_files = {'.gitignore', '.gitattributes', 'build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat', 'LICENSE'}
paths = [x for x in project.iterdir() if x.is_file() and (x.name in allowed_files or x.suffix == '.md')]
for folder in ('src', 'gradle', 'tools', 'validation'):
    paths.extend(x for x in (project / folder).rglob('*') if x.is_file() and '__pycache__' not in x.parts and x.name != 'last-launch.json')
with tempfile.TemporaryDirectory() as temporary:
    archive = pathlib.Path(temporary) / (name + '-project.zip')
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as target:
        for file in sorted(paths):
            if file.is_symlink(): raise ValueError('Refusing source symlink: ' + str(file))
            target.write(file, 'DragonSurvivalRenderOptimizer/' + file.relative_to(project).as_posix())
    shutil.copyfile(archive, dist / archive.name)
manifest = {'name': 'Dragon Survival Render Optimizer', 'abbreviation': 'DSRO', 'version': version, 'modId': 'dsbr', 'defaultMode': 'GPU', 'gpuDefaultEnabled': True, 'formalAcceptanceComplete': formal, 'githubReleaseAssets': [name + '.jar'], 'artifacts': []}
manifest['prerelease'] = prerelease
manifest['releaseAcceptanceComplete'] = stable_evidence is not None
if stable_evidence is not None:
    manifest['stableEvidence'] = 'validation/0.2.1/summary.json'
    manifest['testedJarSha256'] = candidate_hash
    manifest['lowEndDefaultEnabled'] = False
    manifest['startupSmoke'] = {'pass': True, 'path': 'validation/0.2.1/runtime/result.json'}
    manifest['knownLimitations'] = stable_evidence['limitations']
if single_round is not None:
    manifest['singleRoundEvidence'] = {'path': 'validation/alpha4/summary.json', 'pass': single_round['pass'],
                                     'complete': single_round['singleRoundComplete'], 'repetitions': 1,
                                     'comparisonToOldVersion': None}
manifest['incompleteEvidenceRelease'] = a.incomplete_prerelease
if single_round is not None:
    manifest['incompleteEvidenceRelease'] = not single_round['pass']
    manifest['knownLimitations'] = single_round['limitations']
    manifest['testedJarSha256'] = single_round['testedJarSha256']
    manifest['brandingVerification'] = single_round['brandingVerification']
    manifest['startupSmoke'] = single_round['startupSmoke']
if a.incomplete_prerelease: manifest['knownIncident'] = 'validation/low-frames/incidents/incident.json'
for file in sorted(dist.glob(name + '*')):
    manifest['artifacts'].append({'name': file.name, 'bytes': file.stat().st_size, 'sha256': hashlib.sha256(file.read_bytes()).hexdigest()})
(dist / 'build-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'name': manifest['name'], 'version': version, 'prerelease': manifest['prerelease'],
                  'formalAcceptanceComplete': formal, 'startupSmokePassed': manifest.get('startupSmoke', {}).get('pass'),
                  'artifacts': manifest['artifacts']}, ensure_ascii=True, indent=2))
