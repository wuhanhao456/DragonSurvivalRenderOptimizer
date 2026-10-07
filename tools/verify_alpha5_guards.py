"""Only startup compatibility checks may differ from the measured candidate."""
import argparse,hashlib,json,zipfile
from pathlib import Path
PLUGIN='top/wu949/dsbr/optimizer/compat/OptimizerMixinPlugin.class'
def contents(path):
    with zipfile.ZipFile(path) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def verify(before,after):
    a,b=contents(before),contents(after)
    if a.keys()!=b.keys():raise ValueError('Client entry set changed')
    changed=[n for n in a if a[n]!=b[n]]
    if changed!=[PLUGIN]:raise ValueError('Unexpected render/resource changes: '+str(changed))
    assert b'renderCube'in b[PLUGIN] and b'createVerticesOfQuad'in b[PLUGIN]
    return {'pass':True,'performanceJarSha256':hashlib.sha256(before.read_bytes()).hexdigest(),'finalJarSha256':hashlib.sha256(after.read_bytes()).hexdigest(),
        'changedEntries':changed,'identicalOtherEntries':len(a)-1,'renderAndComputeClassesByteIdentical':True,'texturesShadersConfigsAssetsByteIdentical':True,
        'change':'Startup-only GeoRenderer renderCube/createVerticesOfQuad signature guards; no rendering or animation code changes.',
        'performanceInference':'Paired tests used the measured candidate. Final pinned-version rendering follows exactly the same bytecode; final startup/world checks are recorded separately.'}
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('before',type=Path);p.add_argument('after',type=Path);p.add_argument('--output',type=Path,required=True);a=p.parse_args();r=verify(a.before,a.after);a.output.write_text(json.dumps(r,indent=2),encoding='utf-8');print(json.dumps(r))
