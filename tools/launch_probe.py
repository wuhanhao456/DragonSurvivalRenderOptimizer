"""Create a fresh isolated client and launch with a fixed offline identity (no account files)."""
from pathlib import Path
import argparse, hashlib, json, os, platform, re, shutil, struct, subprocess, uuid, zipfile, time

def allowed(item):
    if 'rules' not in item: return True
    accepted = False
    for rule in item['rules']:
        target = rule.get('os', {})
        if target.get('name', 'windows') != 'windows' or target.get('arch', 'x86_64') not in ('amd64', 'x86_64'): continue
        if target.get('version') and not re.search(target['version'], platform.version()): continue
        if any(rule.get('features', {}).values()): continue
        accepted = rule['action'] == 'allow'
    return accepted

def unique_paths(paths):
    seen = set(); result = []
    for path in paths:
        key = os.path.normcase(os.path.abspath(str(path)))
        if key not in seen: seen.add(key); result.append(path)
    return result

def supported_library(lib):
    return lib.get('name', '').rsplit(':', 1)[-1] not in ('natives-windows-x86', 'natives-windows-arm64')

def amd64_dll(data):
    if len(data) < 64 or data[:2] != b'MZ': return False
    offset = struct.unpack_from('<I', data, 0x3c)[0]
    return len(data) >= offset + 6 and data[offset:offset+4] == b'PE\0\0' and struct.unpack_from('<H', data, offset+4)[0] == 0x8664

project = Path(__file__).resolve().parents[1]
workspace = project.parent
parser = argparse.ArgumentParser()
parser.add_argument('--runtime', type=Path, default=Path('E:/mc/.minecraft'))
parser.add_argument('--build', type=Path, default=Path(os.environ['LOCALAPPDATA']) / 'Temp/DSBR-build')
parser.add_argument('--pack', type=Path, default=workspace / 'BeLoong')
parser.add_argument('--manifest', type=Path, default=workspace / 'outputs/dragon-animation-compare/ds-2.0.71/BeLoong.json')
parser.add_argument('--game-jar', type=Path, default=workspace / 'outputs/dragon-animation-compare/ds-2.0.71/BeLoong.jar')
parser.add_argument('--java', type=Path, default=Path('C:/Program Files/Java/jdk-21/bin/java.exe'))
parser.add_argument('--iris', action='store_true')
parser.add_argument('--shaderpacks', action='store_true')
parser.add_argument('--compat-only', action='store_true')
parser.add_argument('--old-config', action='store_true', help='Seed a 0.1.x dsbr-client.toml with saved Bedrock settings')
parser.add_argument('--force-legacy', action='store_true', help='Assert unsupported DS ignores even an explicit legacy opt-in')
args = parser.parse_args()
if args.shaderpacks and not args.iris: parser.error('--shaderpacks requires --iris')
if args.compat_only and args.iris: parser.error('--compat-only excludes Iris/DS/Gecko')
if args.force_legacy and not args.old_config: parser.error('--force-legacy requires --old-config')
root = Path(os.environ['LOCALAPPDATA']) / 'Temp' / ('dsbr-validation-' + ('iris-' if args.iris else 'plain-') + time.strftime('%Y%m%d-%H%M%S'))
root.mkdir(); (root / 'mods').mkdir(); (root / 'logs').mkdir(); (root / 'config').mkdir()
if args.old_config:
    (root / 'config/dsbr-client.toml').write_text('[general]\nnormal_render_mode="BEDROCK"\narmor_render_mode="BEDROCK"\nanimation_speed_multiplier=0.5\n' + ('legacy_backend_enabled=true\n' if args.force_legacy else ''), encoding='utf-8')
(root / 'options.txt').write_text('onboardAccessibility:false\npauseOnLostFocus:false\nmaxFps:120\nenableVsync:false\n', encoding='utf-8')
if args.shaderpacks:
    (root / 'shaderpacks').mkdir()
    for pack in (args.pack / 'shaderpacks').glob('*.zip'): shutil.copyfile(pack, root / 'shaderpacks' / pack.name)
for pattern in ([] if args.compat_only else ['*v2.0.71*', '*4.9.3*'] + (['*iris-neoforge*', '*sodium-neoforge*'] if args.iris else [])):
    files = list((args.pack / 'mods').glob(pattern))
    if len(files) != 1: raise ValueError('Ambiguous mod: ' + pattern)
    shutil.copyfile(files[0], root / 'mods' / files[0].name)
shutil.copyfile(next((args.build / 'libs').glob('*alpha.1.jar')), root / 'mods/optimizer.jar')
shutil.copyfile(args.build / 'validation' / ('dsbr-render-fallback-validation.jar' if args.compat_only else 'dsbr-render-validation.jar'), root / 'mods/validation.jar')
shutil.copyfile(args.game_jar, root / args.game_jar.name)
spec = json.loads(args.manifest.read_text(encoding='utf-8'))
libraries = unique_paths([args.runtime / 'libraries' / lib['downloads']['artifact']['path'] for lib in spec['libraries']
    if allowed(lib) and supported_library(lib) and lib.get('downloads', {}).get('artifact')])
for lib in libraries:
    if not lib.is_file(): raise ValueError('Missing runtime library: ' + str(lib))
natives = root / 'natives'; natives.mkdir()
for lib in libraries:
    if 'natives-windows' in lib.name:
        with zipfile.ZipFile(lib) as archive:
            for name in archive.namelist():
                if name.lower().endswith('.dll'):
                    data = archive.read(name)
                    if not amd64_dll(data): raise ValueError('Wrong native architecture')
                    (natives / Path(name).name).write_bytes(data)
values = {'auth_player_name': 'RenderProbe', 'auth_uuid': uuid.UUID(bytes=hashlib.md5(b'OfflinePlayer:RenderProbe').digest(), version=3).hex, 'auth_access_token': '0',
          'clientid': '', 'auth_xuid': '', 'user_type': 'legacy', 'version_type': 'release', 'version_name': args.game_jar.stem,
          'game_directory': str(root), 'assets_root': str(args.runtime / 'assets'), 'assets_index_name': spec['assetIndex']['id'],
          'natives_directory': str(natives), 'launcher_name': 'BeLoongRenderValidation', 'launcher_version': '1',
          'classpath': os.pathsep.join(map(str, [*libraries, root / args.game_jar.name])), 'library_directory': str(args.runtime / 'libraries'), 'classpath_separator': os.pathsep}
def expand(items):
    output = []
    for item in items:
        if isinstance(item, dict):
            if not allowed(item): continue
            item = item['value']
        for value in item if isinstance(item, list) else [item]: output.append(re.sub(r'\$\{([^}]+)\}', lambda m: values[m[1]], value))
    return output
jvm = expand(spec['arguments']['jvm'])
for i, value in enumerate(jvm[:-1]):
    if value in ('-p', '--module-path', '-cp', '-classpath', '--class-path'): jvm[i+1] = os.pathsep.join(map(str, unique_paths(jvm[i+1].split(os.pathsep))))
argv = ['-Xms1G', '-Xmx4G', '-Dbeloongrender.probe=' + str(not args.compat_only).lower(), '-Dbeloongrender.compatProbe=' + str(args.compat_only).lower(), '-Dbeloongrender.shaders=' + str(args.shaderpacks).lower(), '-Dmixin.debug.verbose=true', *jvm, spec['mainClass'], *expand(spec['arguments']['game']), '--width', '1280', '--height', '720']
argfile = root / 'launch-args.txt'
argfile.write_text('\n'.join('"' + arg.replace('\\', '\\\\').replace('"', '\\"') + '"' for arg in argv), encoding='utf-8')
with (root / 'logs/console.log').open('wb') as stream:
    process = subprocess.Popen([str(args.java), '@' + str(argfile)], cwd=root, stdout=stream, stderr=subprocess.STDOUT,
                               creationflags=subprocess.CREATE_NO_WINDOW)
record = {'pid': process.pid, 'instance': str(root), 'iris': args.iris, 'started': time.time()}
print(json.dumps(record)); (project / 'validation/last-launch.json').write_text(json.dumps(record, indent=2), encoding='utf-8')
