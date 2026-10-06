"""Freeze canonical tracked pack resources locally before A/B; never copy saves/accounts."""
import argparse, hashlib, json, pathlib, shutil
from pack_fixture import copy_folder, copy_mods

def main():
    p=argparse.ArgumentParser(); p.add_argument('--pack',required=True,type=pathlib.Path); p.add_argument('--output',required=True,type=pathlib.Path); a=p.parse_args()
    a.output.mkdir(exist_ok=False); (a.output/'mods').mkdir()
    for folder in ('config','kubejs','ldlib2','hotai'): copy_folder(a.pack,a.output,folder)
    selection=copy_mods(a.pack,a.output/'mods')
    (a.output/'shaderpacks').mkdir()
    for source in sorted((a.pack/'shaderpacks').glob('*.zip')): shutil.copyfile(source,a.output/'shaderpacks'/source.name)
    files={f.relative_to(a.output).as_posix():hashlib.sha256(f.read_bytes()).hexdigest() for f in a.output.rglob('*') if f.is_file()}
    provenance={'source':str(a.pack.resolve()),'policy':'Git-tracked working-tree config/KubeJS/Hotai/mods; existing saves and launcher identity excluded','mods':selection,'files':files}
    (a.output/'fixture-provenance.json').write_text(json.dumps(provenance,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'fixture':str(a.output),'mods':len(selection['selected']),'files':len(files)}))

if __name__=='__main__': main()
