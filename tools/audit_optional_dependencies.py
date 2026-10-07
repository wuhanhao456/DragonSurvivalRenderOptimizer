"""Reject optional Core binary links/assets/dependencies; ordinary name strings are allowed."""
import argparse, hashlib, json, struct, zipfile
from pathlib import Path

def constants(data):
    if data[:4] != b'\xca\xfe\xba\xbe': raise ValueError('Invalid class')
    count=struct.unpack_from('>H',data,8)[0]; pos=10; index=1; values={}; classes=[]
    sizes={3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}
    while index<count:
        tag=data[pos];pos+=1
        if tag==1:
            size=struct.unpack_from('>H',data,pos)[0];pos+=2
            values[index]=data[pos:pos+size].decode('utf-8',errors='replace');pos+=size
        else:
            if tag==7:classes.append(struct.unpack_from('>H',data,pos)[0])
            pos+=sizes[tag]
            if tag in (5,6):index+=1
        index+=1
    return values,classes

def audit(path):
    checked=0
    with zipfile.ZipFile(path) as jar:
        for name in jar.namelist():
            if name.startswith(('com/zonlong/','assets/beloong/')):raise ValueError('Bundled Core content: '+name)
            if '/validation/' in name:raise ValueError('Test driver bundled: '+name)
            if not name.endswith('.class'):continue
            values,classes=constants(jar.read(name));checked+=1
            if any(values[c].startswith('com/zonlong/') or '[Lcom/zonlong/' in values[c] for c in classes):raise ValueError('Core class link: '+name)
            # Includes fields, method descriptors, generic signatures, annotation types and method handles.
            if any('Lcom/zonlong/' in value for value in values.values()):raise ValueError('Core type descriptor: '+name)
        import tomllib
        manifest=tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
        dependencies=manifest.get('dependencies',{}).get('dsbr',[])
        if any(d['modId'] in ('beloong','iris','sodium') and d.get('type')=='required' for d in dependencies):raise ValueError('Mandatory optional dependency')
    return {'pass':True,'sha256':hashlib.sha256(Path(path).read_bytes()).hexdigest(),'classesChecked':checked,
            'coreTypeLinks':0,'bundledCoreContent':0,'testDriverBundled':False,'method':'Class constant and type descriptor audit; dotted class-name strings allowed'}

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('jar',type=Path);p.add_argument('--output',type=Path);a=p.parse_args();result=audit(a.jar)
    if a.output:a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result))
