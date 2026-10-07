"""Publish only bounded test evidence, never accounts, launch arguments, logs or raw JFR."""
import argparse, hashlib, json, pathlib, shutil, xml.etree.ElementTree as ET
from low_metrics import metrics
from run_alpha5_short import compare, SCENES

PROJECT=pathlib.Path(__file__).resolve().parents[1]
def read(path):return json.loads(path.read_text(encoding='utf-8'))
def write(path,data):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
RUNTIME_RESULTS={'probe-result.json','alpha5-scenes.json','alpha5-result.json','npc-smoke-scenes.json','npc-smoke-result.json',
    'single-round-scenes.json','single-round-result.json','single-round-fixture-failure.json','baseline-textures.json','equivalent-state.json','changed-skin.json',
    'changed-armor.json','restored-skin.json','gpu-mode.json','after-reload.json','inventory-vanilla.json','inventory-textures.json','inventory-gpu.json','reload-diagnostic-result.json'}
def archive_rows(inputs,out):
    rows=[]
    for folder,label in inputs:
        for file in sorted(folder.glob('*-baseline.json'))+sorted(folder.glob('*-candidate.json')):
            row=read(file);root=pathlib.Path(row.pop('instance'));stem=f"{label}/{row['scene']}-{row['version']}";dest=out/'samples'/stem;dest.mkdir(parents=True,exist_ok=True)
            raw=root/row['row']['rawFrames'];assert digest(raw)==row['rawSha256'];assert metrics(raw)==row['metrics']
            assert digest(root/'mods/optimizer.jar')==row['jarSha256'];assert digest(root/'mods/validation.jar')==row['driverSha256']
            assert row['result']['pass'] and not row['result']['meshBytesAfterClear'] and not row['result']['textureBytesAfterClear']
            assert row['metrics']['capturedSeconds']>=59.4
            for path in (raw,root/(row['scene']+'.png')):
                shutil.copyfile(path,dest/path.name)
            row['runLabel']=label;row['rawFrames']='samples/'+stem+'/'+raw.name;row['screenshot']='samples/'+stem+'/'+row['scene']+'.png'
            # Instance basename is sufficient to trace the private, locally retained original.
            row['localEvidenceId']=root.name
            write(dest/'result.json',row);rows.append(row)
    return rows
def archive_runtime(root,name,out):
    dest=out/'runtime'/name;dest.mkdir(parents=True,exist_ok=True)
    for path in root.iterdir():
        if path.is_file() and (path.suffix in ('.png','.csv') or path.name in RUNTIME_RESULTS):
            shutil.copyfile(path,dest/path.name)
    manifest=[{'file':p.name,'bytes':p.stat().st_size,'sha256':digest(p)} for p in sorted((root/'mods').glob('*.jar'))]
    write(dest/'actual-mods.json',manifest)
    write(dest/'identity.json',{'instanceId':root.name,'jarSha256':digest(root/'mods/optimizer.jar'),'driverSha256':digest(root/'mods/validation.jar')})

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--suite',type=pathlib.Path,action='append',default=[]);p.add_argument('--recheck',type=pathlib.Path);p.add_argument('--build',type=pathlib.Path,required=True)
    p.add_argument('--runtime',type=pathlib.Path,action='append',default=[]);p.add_argument('--out',type=pathlib.Path,default=PROJECT/'validation/alpha5');a=p.parse_args()
    inputs=[(f,'initial') for f in a.suite]+([(a.recheck,'recheck')] if a.recheck else [])
    rows=archive_rows(inputs,a.out);comparisons=[]
    for label,scene in sorted({(r['runLabel'],r['scene']) for r in rows}):
        pair={r['version']:r for r in rows if r['runLabel']==label and r['scene']==scene}
        if set(pair)=={'baseline','candidate'}:comparisons.append({'runLabel':label,**compare(pair['baseline'],pair['candidate'])})
    roots=[ET.parse(f).getroot().attrib for f in (a.build/'test-results/test').glob('TEST-*.xml')]
    tests={k:sum(int(r[k]) for r in roots) for k in ('tests','failures','errors','skipped')}
    for root in a.runtime:archive_runtime(root,root.name,a.out)
    final={c['scene']:c for c in comparisons if c['runLabel']=='initial'}
    final.update({c['scene']:c for c in comparisons if c['runLabel']=='recheck'})
    complete=set(final)==set(SCENES)
    summary={'version':'0.2.0-alpha.5','formalAcceptanceComplete':False,'exploratory':True,'warmupSeconds':15,'sampleSeconds':60,'repetitions':1,'complete':complete,'unitTests':tests,
             'hardware':{'cpu':'Intel Core i7-13700K','gpu':'NVIDIA GeForce RTX 5070 Ti','ramGiB':128,'driver':'596.49','java':'Oracle 21.0.8','os':'Windows'},
             'comparisonTo':'0.2.0-alpha.4 GPU','shaderPack':'ComplementaryUnbound_r5.9.zip','display':{'width':1280,'height':720,'viewDistance':6,'vsync':False,'unlimitedFps':True},
             'jvmMemory':['-Xms1G','-Xmx8G'],'comparisons':comparisons,'finalComparisons':list(final.values()),
             'performanceGuardPass':complete and all(not c['recheckRequired'] for c in final.values()),'samples':rows,
             'limitations':['One short pair per scene; only a triggered regression receives one targeted recheck.',
                 'The user\'s running server client remains open. These are isolated local render scenes, not a server A/B test.',
                 'No claim about the original spark-report hardware or a 3 x 300 s formal Low acceptance.',
                 'NPC geometry timer includes the original bone traversal and capture/submission, but excludes animation evaluation before actuallyRender and deferred drawing.']}
    write(a.out/'summary.json',summary)
    lines=['# DSRO alpha.5 配对短测','',f"硬件：i7-13700K、RTX 5070 Ti、128 GiB；NVIDIA 596.49，Oracle Java 21.0.8。",'',
        '每景 15 秒预热、60 秒完整帧间隔、一次配对；基线 alpha.4 与候选均为 GPU 模式。Complementary Unbound r5.9、1280×720、视距 6、关闭垂直同步和帧率限制、8 GiB 堆。没有在性能采样中开启 JFR 或 Spark。', '',
        '| 场景 / 轮次 | 平均 FPS 变化 | 1% Low 变化 | 0.1% Low 变化 | P95 变化 | NPC 几何 CPU 耗时下降 |',
        '| --- | ---: | ---: | ---: | ---: | ---: |']
    for c in comparisons:
        npc=f"{c['npcGeometryCpuReductionPercent']:.1f}%" if 'npcGeometryCpuReductionPercent'in c else '—'
        lines.append(f"| {c['scene']} / {c['runLabel']} | {c['averageFpsChangePercent']:+.1f}% | {c['low1ChangePercent']:+.1f}% | {c['low01ChangePercent']:+.1f}% | {c['p95ChangePercent']:+.1f}% | {npc} |")
    lines+=['','Low FPS = 1000 ÷ 最慢对应比例帧时间的平均值（ms），尾部样本向上取整。P95/P99/P99.9 不是 Low FPS。完整 CSV 和场景截图保留；无效启动不加入比较。','',
        '平均 FPS 下降超过 5% 或 P95 增加超过 5% 触发一次复核；持续退步的改动不得发布。复核行保留初测，最终判断采用复核结果，不能挑选较好的一次。','',
        'NPC 计时包含骨骼遍历及几何捕获/提交，不包含之前的动画求值和之后的延迟绘制。动画仍由原模组逐帧处理。同帧动画复用实验没有缓存命中，已撤出发布源码。','',
        '用户原服务器客户端保持打开，未修改其整包或世界；这属于本机隔离渲染探索，不是原服务器或原报告机器的性能保证。短测不等同于原 3×300 秒正式 Low 验收。', '',
        f"场景完整：{complete}；性能保护线通过：{summary['performanceGuardPass']}；Java 测试 {tests['tests']} 项，失败 {tests['failures']}，错误 {tests['errors']}。",'']
    (a.out/'SUMMARY.md').write_text('\n'.join(lines),encoding='utf-8')
    print(json.dumps({'complete':complete,'performanceGuardPass':summary['performanceGuardPass'],'samples':len(rows)},ensure_ascii=False))
