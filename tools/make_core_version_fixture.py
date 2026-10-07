"""Private test-only metadata mismatch; never publish or install this altered third-party jar."""
import argparse,hashlib,json,zipfile
from pathlib import Path
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('directory',type=Path);a=p.parse_args()
    if not a.directory.name.startswith('dsbr-validation-'):raise ValueError('Isolated fixture directory required')
    a.directory.mkdir(parents=True,exist_ok=True);dest=a.directory/'beloong-test-version-mismatch.jar'
    with zipfile.ZipFile(a.source) as src,zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED) as target:
        for info in src.infolist():
            data=src.read(info.filename)
            if info.filename=='META-INF/neoforge.mods.toml':
                assert data.count(b'version="0.10.1"')==1;data=data.replace(b'version="0.10.1"',b'version="0.10.2"')
            target.writestr(info,data)
    print(json.dumps({'testOnly':True,'file':dest.name,'version':'0.10.2','sourceSha256':hashlib.sha256(a.source.read_bytes()).hexdigest(),'sha256':hashlib.sha256(dest.read_bytes()).hexdigest()}))
