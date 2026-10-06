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
parser.add_argument('--runtime', type=Path, default=Path('C:/mc/.minecraft'))
parser.add_argument('--build', type=Path, default=Path(os.environ['LOCALAPPDATA']) / 'Temp/DSBR-build')
parser.add_argument('--pack', type=Path, default=workspace / 'BeLoong')
parser.add_argument('--manifest', type=Path, default=workspace / 'outputs/dragon-animation-compare/ds-2.0.71/BeLoong.json')
parser.add_argument('--game-jar', type=Path, default=workspace / 'outputs/dragon-animation-compare/ds-2.0.71/BeLoong.jar')
parser.add_argument('--java', type=Path, default=Path('C:/Program Files/Microsoft/jdk-21.0.11.10-hotspot/bin/java.exe'))
parser.add_argument('--iris', action='store_true')
parser.add_argument('--shaderpacks', action='store_true')
parser.add_argument('--compat-only', action='store_true')
parser.add_argument('--default-mode-only', action='store_true', help='Check fresh-config GPU rendering without issuing mode commands')
parser.add_argument('--multi-dragon-benchmark', action='store_true', help='Render-only 1/4/12 player short comparison')
parser.add_argument('--optimizer-jar', type=Path)
parser.add_argument('--counts', default='1,4,12')
parser.add_argument('--warm', type=int, default=5)
parser.add_argument('--sample', type=int, default=15)
parser.add_argument('--repeats', type=int, default=1)
parser.add_argument('--gpu-only', action='store_true')
parser.add_argument('--fullpack', action='store_true')
parser.add_argument('--scenario', choices=['fixed','appearance','flight-new','flight-preloaded'], default='fixed')
parser.add_argument('--heap', default='4G')
parser.add_argument('--jfr', action='store_true')
parser.add_argument('--stage', type=int, default=4)
parser.add_argument('--prepare-world', action='store_true')
parser.add_argument('--world-template', type=Path)
parser.add_argument('--fixture-mods', type=Path, help='Explicit test-only dependency overrides')
parser.add_argument('--visual-scenes', action='store_true')
parser.add_argument('--single-round', action='store_true', help='One candidate-only soul/config/animation validation launch')
args = parser.parse_args()
if args.shaderpacks and not args.iris: parser.error('--shaderpacks requires --iris')
if args.compat_only and args.iris: parser.error('--compat-only excludes Iris/DS/Gecko')
provenance=args.pack/'fixture-provenance.json'
if provenance.is_file():
    original=json.loads(provenance.read_text(encoding='utf-8'))
    actual={path.relative_to(args.pack).as_posix() for path in args.pack.rglob('*') if path.is_file()}
    if actual != set(original['files']) | {'fixture-provenance.json'}: raise ValueError('Frozen fixture file set changed')
    for name,digest in original['files'].items():
        path=args.pack/name
        if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest()!=digest: raise ValueError('Frozen fixture changed: '+name)
root = Path(os.environ['LOCALAPPDATA']) / 'Temp' / ('dsbr-validation-' + ('iris-' if args.iris else 'plain-') + time.strftime('%Y%m%d-%H%M%S'))
root.mkdir(); (root / 'mods').mkdir(); (root / 'logs').mkdir(); (root / 'config').mkdir()
if provenance.is_file(): shutil.copyfile(provenance,root/'fixture-provenance.json')
if args.world_template:
    if not args.world_template.parent.parent.name.startswith('dsbr-validation-'): raise ValueError('Only isolated probe world templates allowed')
    shutil.copytree(args.world_template, root / 'saves/multi-render-validation')
(root / 'options.txt').write_text('onboardAccessibility:false\npauseOnLostFocus:false\nmaxFps:120\nenableVsync:false\n', encoding='utf-8')
if args.shaderpacks:
    (root / 'shaderpacks').mkdir()
    for pack in (args.pack / 'shaderpacks').glob('*.zip'): shutil.copyfile(pack, root / 'shaderpacks' / pack.name)
    if args.default_mode_only or args.multi_dragon_benchmark or args.visual_scenes or args.single_round:
        startup_pack = 'ComplementaryReimagined_r5.9.zip'
        if not (root / 'shaderpacks' / startup_pack).is_file(): raise ValueError('Default-mode probe requires ' + startup_pack)
        (root / 'config/iris.properties').write_text('shadersEnabled=true\nshaderPack=' + startup_pack + '\n', encoding='utf-8')
if args.fullpack:
    from pack_fixture import copy_mods, copy_folder
    for folder in ('config', 'kubejs', 'ldlib2', 'hotai'):
        copy_folder(args.pack, root, folder)
    for file, keys in [('lockdown.toml', ('pin_dimensions_enabled','login_spawn_teleport_enabled')), ('beloong-common.toml', ('enabled',))]:
        path = root / 'config' / file
        if path.exists():
            text = path.read_text(encoding='utf-8')
            for key in keys: text = re.sub(r'(?m)^(\s*' + re.escape(key) + r'\s*=\s*)true\b', r'\g<1>false', text)
            path.write_text(text, encoding='utf-8')
    modern_ui = root / 'config/ModernUI/client.toml'
    if modern_ui.exists():
        text = modern_ui.read_text(encoding='utf-8')
        for key in ('framerateInactive','framerateMinimized'):
            text = re.sub(r'(?m)^(\s*' + key + r'\s*=\s*)\d+\b', r'\g<1>0', text)
        modern_ui.write_text(text, encoding='utf-8')
    spark = root / 'config/spark/config.json'
    if spark.exists():
        config = json.loads(spark.read_text(encoding='utf-8')); config['backgroundProfiler'] = False
        spark.write_text(json.dumps(config,ensure_ascii=False,indent=2),encoding='utf-8')
    # Same canonical fixture policy as outputs/dragon-animation-compare/README.md.
    # The untracked old patch cannot decode against DS 2.0.71; preserve the source file.
    stale = root / 'hotai/by/dragonsurvivalteam/dragonsurvival/common/capability/DragonStateHandler.badiff'
    if stale.exists(): stale.unlink()
    selection = copy_mods(args.pack, root / 'mods')
    if args.fixture_mods:
        from pack_fixture import metadata
        for override in args.fixture_mods.glob('*.jar'):
            info = metadata(override)
            if not info['compatible']: raise ValueError('Incompatible fixture override: ' + override.name)
            for entry in list(selection['selected']):
                if set(entry['ids']).intersection(info['ids']):
                    (root / 'mods' / entry['name']).unlink(); selection['selected'].remove(entry)
                    selection['excluded'].append({'name':entry['name'],'reason':'explicit fixture override'})
            shutil.copyfile(override, root / 'mods' / override.name)
            selection['selected'].append({'name':override.name,**info,'sha256':hashlib.sha256(override.read_bytes()).hexdigest(),'fixtureOverride':True})
    (root / 'fixture-mods.json').write_text(json.dumps(selection, ensure_ascii=False, indent=2), encoding='utf-8')
for pattern in ([] if args.compat_only else ['*v2.0.71*', '*4.9.3*'] + (['*tundradragon-1.5.0*','*ds_aether_addon-1.1.0*'] if args.single_round else []) + (['*iris-neoforge*', '*sodium-neoforge*'] if args.iris else [])):
    files = list((args.pack / 'mods').glob(pattern))
    if len(files) != 1: raise ValueError('Ambiguous mod: ' + pattern)
    shutil.copyfile(files[0], root / 'mods' / files[0].name)
if args.shaderpacks and (args.default_mode_only or args.multi_dragon_benchmark or args.visual_scenes or args.single_round):
    (root / 'config/iris.properties').write_text('shadersEnabled=true\nshaderPack=ComplementaryReimagined_r5.9.zip\n', encoding='utf-8')
from artifact_name import artifact_name
shutil.copyfile(args.optimizer_jar or args.build / 'libs' / (artifact_name(project) + '.jar'), root / 'mods/optimizer.jar')
shutil.copyfile(args.build / 'validation' / ('dsbr-render-fallback-validation.jar' if args.compat_only else 'dsbr-render-validation.jar'), root / 'mods/validation.jar')
shutil.copyfile(args.game_jar, root / args.game_jar.name)
spec = json.loads(args.manifest.read_text(encoding='utf-8'))
# FML's production providers load these artifacts directly, outside the manifest classpath.
game_arguments = spec['arguments']['game']
def game_value(flag): return game_arguments[game_arguments.index(flag) + 1]
neo_version = game_value('--fml.neoForgeVersion')
mc_neoform = game_value('--fml.mcVersion') + '-' + game_value('--fml.neoFormVersion')
for relative in (f'net/neoforged/neoforge/{neo_version}/neoforge-{neo_version}-client.jar',
                 f'net/neoforged/neoforge/{neo_version}/neoforge-{neo_version}-universal.jar',
                 f'net/minecraft/client/{mc_neoform}/client-{mc_neoform}-srg.jar',
                 f'net/minecraft/client/{mc_neoform}/client-{mc_neoform}-extra.jar'):
    if not (args.runtime / 'libraries' / relative).is_file():
        raise ValueError('Incomplete NeoForge installation: ' + relative)
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
argv = ['-Djdk.net.unixdomain.tmpdir=' + str(root / 'unused-socket-dir'), '-Ddsbr.validationStage=' + str(args.stage), '-Xms1G', '-Xmx' + args.heap, *(['-XX:StartFlightRecording=filename=' + str(root / 'diagnostic.jfr') + ',settings=profile,dumponexit=true'] if args.jfr else []), *['-Dbeloongrender.' + k + '=' + str(v).lower() for k,v in {'singleRound':args.single_round, 'visualScenes':args.visual_scenes, 'fullpack':args.fullpack, 'counts':args.counts, 'warm':args.warm, 'sample':args.sample, 'repeats':args.repeats, 'gpuOnly':args.gpu_only, 'scenario':args.scenario, 'prepareWorld':args.prepare_world, 'fixtureWorld':bool(args.world_template)}.items()], '-Dbeloongrender.probe=' + str(not args.compat_only and not args.multi_dragon_benchmark and not args.single_round).lower(), '-Dbeloongrender.multiBenchmark=' + str(args.multi_dragon_benchmark).lower(), '-Dbeloongrender.compatProbe=' + str(args.compat_only).lower(), '-Dbeloongrender.defaultModeOnly=' + str(args.default_mode_only).lower(), '-Dbeloongrender.shaders=' + str(args.shaderpacks).lower(), '-Dmixin.debug.verbose=true', *jvm, spec['mainClass'], *expand(spec['arguments']['game']), '--width', '1280', '--height', '720']
argfile = root / 'launch-args.txt'
argfile.write_text('\n'.join('"' + arg.replace('\\', '\\\\').replace('"', '\\"') + '"' for arg in argv), encoding='utf-8')
with (root / 'logs/console.log').open('wb') as stream:
    process = subprocess.Popen([str(args.java), '@' + str(argfile)], cwd=root, stdout=stream, stderr=subprocess.STDOUT,
                               creationflags=subprocess.CREATE_NO_WINDOW)
record = {'pid': process.pid, 'instance': str(root), 'iris': args.iris, 'started': time.time()}
print(json.dumps(record)); (project / 'validation/last-launch.json').write_text(json.dumps(record, indent=2), encoding='utf-8')
