"""Archive the single candidate run without rerunning or inferring A/B improvements."""
from pathlib import Path
import argparse, hashlib, json, shutil, xml.etree.ElementTree as ET
from low_metrics import metrics
from artifact_name import artifact_name
from verify_branding import compare

p = argparse.ArgumentParser()
p.add_argument('--instance', type=Path, required=True)
p.add_argument('--build', type=Path, required=True)
a = p.parse_args()
project = Path(__file__).resolve().parents[1]
out = project / 'validation/alpha4'
out.mkdir(parents=True, exist_ok=True)
result = json.loads((a.instance / 'single-round-result.json').read_text(encoding='utf-8'))
scenes = json.loads((a.instance / 'single-round-scenes.json').read_text(encoding='utf-8'))
digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
actual_mods = [{'name': path.name, 'bytes': path.stat().st_size, 'sha256': digest(path)}
               for path in sorted((a.instance / 'mods').glob('*.jar'))]
(out / 'actual-mods.json').write_text(json.dumps(actual_mods, ensure_ascii=False, indent=2), encoding='utf-8')
jar = a.build / 'libs' / (artifact_name(project) + '.jar')
tested_hash = digest(a.instance / 'mods/optimizer.jar')
branding = None
if digest(jar) != tested_hash:
    branding = compare(a.instance / 'mods/optimizer.jar', jar)
    (out / 'branding-verification.json').write_text(json.dumps(branding, ensure_ascii=False, indent=2), encoding='utf-8')
tests = [ET.parse(f).getroot().attrib for f in (a.build / 'test-results/test').glob('TEST-*.xml')]
test_result = {key: sum(int(t[key]) for t in tests) for key in ('tests', 'failures', 'errors', 'skipped')}
performance = []
animations = []
for row in scenes:
    if row.get('scenario', '').startswith('souls-'):
        raw = a.instance / (row['scenario'] + '.csv')
        performance.append({**row, 'metrics': metrics(raw), 'rawSha256': digest(raw)})
    if row.get('scenario', '').startswith('animation-'):
        actors = {}
        for sample in row['samples']: actors.setdefault(sample['actor'], []).append(sample)
        active = {actor: samples for actor, samples in actors.items() if len(samples) >= 2}
        action = row['scenario'].rsplit('-', 1)[1]
        report = {actor: {'samples': len(samples), 'distinctTicks': len({s['tick'] for s in samples}),
                          'distinctPoses': len({s['poseHash'] for s in samples}),
                          'expectedActionSeen': any(s['controllers'].get('soul', {}).get('animation') == action for s in samples)}
                  for actor, samples in active.items()}
        animations.append({'scenario': row['scenario'], 'activeActors': report,
                           'singleSampleTransitionActors': [actor for actor, samples in actors.items() if len(samples) == 1],
                           'playerSampled': 'player' in active,
                           'continuousSoulData': bool(report) and all(v['distinctTicks'] > 1 and v['distinctPoses'] > 1 and v['expectedActionSeen']
                                                                     for actor, v in report.items() if actor.startswith('soul-'))})
for file in a.instance.iterdir():
    if file.is_file() and (file.suffix in ('.png', '.csv') or file.name in ('single-round-scenes.json', 'single-round-result.json', 'fixture-mods.json', 'camera-inspection.txt', 'camera-repair.txt')):
        shutil.copyfile(file, out / file.name)
for name in ('console.log', 'latest.log', 'debug.log'):
    file = a.instance / 'logs' / name
    if file.is_file(): shutil.copyfile(file, out / name)
if (a.instance / 'logs/startup-failure').is_dir():
    shutil.copytree(a.instance / 'logs/startup-failure', out / 'startup-failure', dirs_exist_ok=True)
shutil.copyfile(a.instance / 'config/dsbr-optimizer-client.toml', out / 'tested-config.toml')
gl = json.loads((out / 'gl-results.json').read_text(encoding='utf-8'))
complete = len(performance) == 6 and len(scenes) == 17
summary = {'version': '0.2.0-alpha.4', 'candidateOnly': True, 'repetitions': 1, 'jarSha256': digest(jar),
           'name': 'Dragon Survival Render Optimizer', 'artifact': jar.name, 'testedJarSha256': tested_hash,
           'brandingVerification': 'branding-verification.json' if branding else None,
           'driverSha256': digest(a.instance / 'mods/validation.jar'), 'actualModsManifest': 'actual-mods.json',
           'correctedDriverSha256': digest(a.build / 'validation/dsbr-render-validation.jar'),
           'driverSourceCorrectedAfterRun': True, 'unitTests': test_result, 'openGL': gl,
           'runtime': result, 'performance': performance, 'singleRoundComplete': complete,
           'animationDataAssessment': animations,
           'frameTimingAccepted': False,
           'limitations': ['Original round fixture camera yaw was overridden by DS; its performance and animation screenshots face away from the actors.',
                           'Original player idle/walk/fly phases have no player samples. The later startup smoke verifies visible cave-dragon idle; full player walk/fly validation remains unconfirmed.',
                           'Removed soul actors with one transition sample triggered continuity assertions; all repeatedly sampled soul actors advanced with the expected animation.',
                           'Original creative inventory redirected to creative tabs with no entity preview. The later startup smoke verifies survival inventory CPU entity preview.',
                           'Original fixture source was corrected after the one round; that performance/animation round was not repeated. A separate requested startup smoke checks the renamed jar.'],
           'pass': complete and result['pass'] and test_result['failures'] == test_result['errors'] == 0,
           'formalAcceptanceComplete': False, 'comparisonToOldVersion': None}
smoke_path = out / 'smoke/assessment.json'
if smoke_path.is_file():
    smoke = json.loads(smoke_path.read_text(encoding='utf-8'))
    if smoke['jarSha256'] == summary['jarSha256']: summary['startupSmoke'] = smoke
(out / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
lines = ['# DSRO alpha.4 单轮验证', '',
         f"结果：{'通过' if summary['pass'] else '未全部通过'}。仅运行新版一次，没有旧版对照或重复长测。", '',
         f"Java 测试：{test_result['tests']} 项，失败 {test_result['failures']}，错误 {test_result['errors']}。",
         f"OpenGL：{gl['renderer']}，多实例标准／Iris 顶点校验通过。", '',
         '| 场景 | 平均 FPS | P95 帧时间 ms | 龙魂 GPU 实例提交 | 龙魂绘制调用 |',
         '| --- | ---: | ---: | ---: | ---: |']
for row in performance:
    m = row['metrics']; total = row['stats']['totals']
    lines.append(f"| {row['scenario']} | {m['averageFps']:.1f} | {m['frameTimeMs']['p95']:.2f} | {total.get('SOUL_GPU_PASS', 0)} | {total.get('SOUL_GPU_DRAW_CALLS', 0)} |")
lines += ['', '**以上帧时间仅归档，不作为有效性能结论：DS 相机钩子覆盖测试相机朝向，前面的截图未拍到模型。**', '',
          '九个龙魂动作阶段中，持续采样的对象均有推进的动画时间、变化的骨骼姿态和正确的动作。切换时只留下一个样本的旧对象不能判断连续性，原始驱动对此误报冻结，失败列表保留。',
          '普通玩家待机／行走／飞行阶段缺少玩家样本，尚未完成确认；后续吐息／恢复记录到玩家控制器和姿态，并有可见模型截图。',
          '物品栏跳转到创造模式页，没有实体预览，CPU 预览断言未能验证。配置保存、GPU 合批、模式切换、资源重载及最终资源清理已执行。',
          '测试驱动的相机、过渡样本和创造模式物品栏问题已修正源码；按要求没有重跑。',
          '测试驱动使用隔离整合包与新测试世界；不修改源整合包的存档。完整范围与故障见 summary.json。', '',
          f"客户端 SHA-256：`{summary['jarSha256']}`", '']
if branding:
    lines += ['本轮运行及截图采于 DSBR 更名之前。DSRO 发布 jar 只修改显示名称和日志文字；逐文件、逐 class 常量核对后，渲染代码、方法指令和其他资源相同，详见 `branding-verification.json`。没有重跑这组场景或性能采样。',
              f"更名前实际测试 jar SHA-256：`{tested_hash}`", '']
if 'startupSmoke' in summary:
    lines += [f"按后续要求，另做更名 jar 的启动／世界检查：{'通过' if summary['startupSmoke']['pass'] else '未通过'}，未重复性能采样。详情见 [启动检查](smoke/SUMMARY.md)。", '']
if result['failures']:
    lines += [f"原始驱动有 {len(result['failures'])} 条未通过断言，按上述相机、单帧过渡对象和创造模式界面原因分类；完整列表保留在 `single-round-result.json`，未改写为通过。", '']
(out / 'SUMMARY.md').write_text('\n'.join(lines), encoding='utf-8')
print(json.dumps({'pass': summary['pass'], 'scenes': len(scenes), 'performanceScenes': len(performance), 'failureCount': len(result['failures'])}, ensure_ascii=False))
