"""Package only optimizer source/evidence; never include runtime dependencies or user state."""
import argparse, hashlib, json, os, pathlib, shutil, tempfile, zipfile

project = pathlib.Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(); p.add_argument('--build', type=pathlib.Path, default=pathlib.Path(os.environ['LOCALAPPDATA']) / 'Temp/DSBR-build'); p.add_argument('--incomplete-prerelease',action='store_true',help='Package a clearly labelled test build after the user stops remaining runtime tests'); a = p.parse_args()
dist = project / 'dist'; dist.mkdir(exist_ok=True)
version = next(x.split('=', 1)[1] for x in (project / 'gradle.properties').read_text().splitlines() if x.startswith('mod_version='))
name = 'dsbr-' + version
formal = False
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
    assert 'LICENSE' in members and 'THIRD_PARTY_NOTICES.md' in members and 'dsbr.mixins.json' in members
allowed_files = {'.gitignore', 'build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat', 'LICENSE'}
paths = [x for x in project.iterdir() if x.is_file() and (x.name in allowed_files or x.suffix == '.md')]
for folder in ('src', 'gradle', 'tools', 'validation'):
    paths.extend(x for x in (project / folder).rglob('*') if x.is_file() and '__pycache__' not in x.parts and x.name != 'last-launch.json')
with tempfile.TemporaryDirectory() as temporary:
    archive = pathlib.Path(temporary) / (name + '-project.zip')
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as target:
        for file in sorted(paths):
            if file.is_symlink(): raise ValueError('Refusing source symlink: ' + str(file))
            target.write(file, 'DragonSurvivalBedrockRenderer/' + file.relative_to(project).as_posix())
    shutil.copyfile(archive, dist / archive.name)
manifest = {'version': version, 'modId': 'dsbr', 'defaultMode': 'GPU', 'gpuDefaultEnabled': True, 'formalAcceptanceComplete': formal, 'githubReleaseAssets': [name + '.jar'], 'artifacts': []}
manifest['prerelease'] = True
manifest['incompleteEvidenceRelease'] = a.incomplete_prerelease
if a.incomplete_prerelease: manifest['knownIncident'] = 'validation/low-frames/incidents/incident.json'
for file in sorted(dist.glob(name + '*')):
    manifest['artifacts'].append({'name': file.name, 'bytes': file.stat().st_size, 'sha256': hashlib.sha256(file.read_bytes()).hexdigest()})
(dist / 'build-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(manifest, ensure_ascii=False, indent=2))
