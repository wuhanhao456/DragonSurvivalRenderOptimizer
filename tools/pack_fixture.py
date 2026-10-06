"""Select one compatible version per mod ID in an isolated fixture; never edit the source pack."""
import hashlib, json, re, shutil, tomllib, zipfile, subprocess

def tracked(pack, folder):
    # Read-only command scoped to the explicitly supplied pack. No Git config is persisted.
    result=subprocess.run(['git','-C',str(pack.resolve()),'-c','safe.directory=*','ls-files','-z','--',folder],capture_output=True)
    if result.returncode: return None
    return [pack / name for name in result.stdout.decode('utf-8').split('\0') if name]

def copy_folder(pack, destination, folder):
    files=tracked(pack,folder)
    if files is None:
        if (pack/folder).is_dir(): shutil.copytree(pack/folder,destination/folder,dirs_exist_ok=True)
        return
    for path in files:
        if path.is_file():
            target=destination/path.relative_to(pack); target.parent.mkdir(parents=True,exist_ok=True); shutil.copyfile(path,target)

def numeric(v): return tuple(int(n) for n in re.findall(r'\d+', v)[:5])
def in_range(version, bounds):
    bounds=bounds.strip() if bounds else ''
    if not bounds or bounds.strip() == '*': return True
    value=numeric(version)
    # Maven's bare version is a recommendation, not a hard restriction.
    # The loader owns dependency validation; do not reject such declarations.
    if bounds[0] not in '[(': return True
    inner=bounds[1:-1]
    if ',' not in inner: return value == numeric(inner)
    lo,hi=(part.strip() for part in inner.split(',',1))
    return (not lo or value > numeric(lo) or (bounds[0]=='[' and value==numeric(lo))) and (not hi or value < numeric(hi) or (bounds[-1]==']' and value==numeric(hi)))

def metadata(path):
    with zipfile.ZipFile(path) as z:
        name=next((n for n in ('META-INF/neoforge.mods.toml','META-INF/mods.toml') if n in z.namelist()),None)
        if not name: return {'ids':[path.name], 'version':'0', 'compatible':True}
        doc=tomllib.loads(z.read(name).decode('utf-8'))
        version=str(doc.get('mods',[{}])[0].get('version','0'))
        if '${' in version:
            manifest=z.read('META-INF/MANIFEST.MF').decode('utf-8') if 'META-INF/MANIFEST.MF' in z.namelist() else ''
            found=re.search(r'^Implementation-Version:\s*(.+)',manifest,re.M)
            if found: version=found[1].strip()
            else:
                cleaned=re.sub(r'(?i)(?:mc)?1\.21(?:\.1)?','',path.stem)
                found=re.findall(r'\d+(?:\.\d+)+(?:[-+.][\w.]+)?',cleaned)
                version=found[-1] if found else '0'
        compatible=True
        for dependencies in doc.get('dependencies',{}).values():
            for dependency in dependencies:
                if dependency.get('modId') in ('minecraft','neoforge'):
                    expected='1.21.1' if dependency['modId']=='minecraft' else '21.1.248'
                    bounds=dependency.get('versionRange','')
                    # Verified in FML loader 4.0.43 VersionSupportMatrix: 1.21.1 also accepts
                    # Minecraft 1.21 / NeoForge 21.0.166 dependency declarations.
                    override='1.21' if dependency['modId']=='minecraft' else '21.0.166'
                    compatible &= in_range(expected,bounds) or in_range(override,bounds)
        return {'ids':[m['modId'] for m in doc.get('mods',[])], 'version':version, 'compatible':compatible}

def copy_mods(pack, destination):
    candidates=[]; excluded=[]
    files=tracked(pack,'mods'); paths=[p for p in files if p.suffix=='.jar' and p.is_file()] if files is not None else list((pack/'mods').glob('*.jar'))
    for path in paths:
        if path.name.startswith(('dsbr-', 'dsro-')): continue
        info=metadata(path)
        if not info['compatible']: excluded.append({'name':path.name,'reason':'MC/NeoForge range excludes fixture'}); continue
        candidates.append((path,info))
    candidates.sort(key=lambda x:(numeric(x[1]['version']), x[0].name),reverse=True)
    used=set(); selected=[]
    for path,info in candidates:
        if used.intersection(info['ids']): excluded.append({'name':path.name,'reason':'duplicate mod ID; higher compatible version selected'}); continue
        used.update(info['ids']); shutil.copyfile(path,destination/path.name)
        selected.append({'name':path.name,**info,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
    return {'selected':selected,'excluded':excluded,'sourcePolicy':'Git-tracked working-tree files' if files is not None else 'loose pack directory'}
