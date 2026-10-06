"""Launch the renamed candidate in a copy of an already isolated fixture, without performance resampling."""
from pathlib import Path
import argparse, hashlib, json, os, shutil, subprocess, time
from artifact_name import artifact_name

p=argparse.ArgumentParser()
p.add_argument('--fixture',type=Path,required=True);p.add_argument('--build',type=Path,required=True)
p.add_argument('--java',type=Path,default=Path('C:/Program Files/Microsoft/jdk-21.0.11.10-hotspot/bin/java.exe'))
a=p.parse_args();project=Path(__file__).resolve().parents[1]
if not a.fixture.name.startswith('dsbr-validation-') or not (a.fixture/'saves/single-round').is_dir():
    raise ValueError('Only an existing isolated single-round fixture is allowed')
root=Path(os.environ['LOCALAPPDATA'])/'Temp'/('dsbr-validation-smoke-'+time.strftime('%Y%m%d-%H%M%S'));root.mkdir()
for folder in ('mods','config','kubejs','ldlib2','hotai','CustomSkinLoader','shaderpacks','natives'):
    if (a.fixture/folder).is_dir():shutil.copytree(a.fixture/folder,root/folder)
shutil.copytree(a.fixture/'saves/single-round',root/'saves/single-round')
for name in ('BeLoong.jar','options.txt','fixture-mods.json'):
    if (a.fixture/name).is_file():shutil.copyfile(a.fixture/name,root/name)
jar=a.build/'libs'/(artifact_name(project)+'.jar');shutil.copyfile(jar,root/'mods/optimizer.jar')
shutil.copyfile(a.build/'validation/dsbr-render-validation.jar',root/'mods/validation.jar')
args=(a.fixture/'launch-args.txt').read_text(encoding='utf-8').replace(str(a.fixture).replace('\\','\\\\'),str(root).replace('\\','\\\\'))
args=args.replace('-Dbeloongrender.singleRound=true','-Dbeloongrender.singleRound=false').replace('-Dmixin.debug.verbose=true','-Dmixin.debug.verbose=false')
args='"-Dbeloongrender.smoke=true"\n'+args
if str(a.fixture).replace('\\','\\\\') in args:raise ValueError('Fixture path left in launch args')
(root/'launch-args.txt').write_text(args,encoding='utf-8');(root/'logs').mkdir()
with (root/'logs/console.log').open('wb') as stream:
    process=subprocess.Popen([str(a.java),'@'+str(root/'launch-args.txt')],cwd=root,stdout=stream,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
record={'pid':process.pid,'instance':str(root),'sourceFixture':str(a.fixture),'kind':'startup-world-smoke','started':time.time(),
        'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'performanceResampled':False}
(root/'smoke-launch.json').write_text(json.dumps(record,indent=2),encoding='utf-8')
(project/'validation/last-launch.json').write_text(json.dumps(record,indent=2),encoding='utf-8');print(json.dumps(record))
