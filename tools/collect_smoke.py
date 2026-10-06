"""Archive the requested DSRO startup/world check separately from the original performance round."""
from pathlib import Path
import argparse, hashlib, json, shutil
from artifact_name import artifact_name

p=argparse.ArgumentParser();p.add_argument('--instance',type=Path,required=True);p.add_argument('--build',type=Path,required=True)
p.add_argument('--visual-verified',action='store_true',help='World and inventory screenshots have been visually reviewed')
a=p.parse_args();project=Path(__file__).resolve().parents[1];out=project/'validation/alpha4/smoke';out.mkdir(parents=True,exist_ok=True)
digest=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
result=json.loads((a.instance/'smoke-result.json').read_text(encoding='utf-8'));launch=json.loads((a.instance/'smoke-launch.json').read_text(encoding='utf-8'))
jar=a.build/'libs'/(artifact_name(project)+'.jar')
if digest(jar)!=digest(a.instance/'mods/optimizer.jar') or digest(jar)!=launch['jarSha256']:raise ValueError('Smoke artifact identity mismatch')
for name in ('smoke-result.json','smoke-launch.json','smoke-title.png','smoke-world.png','smoke-inventory.png'):
    shutil.copyfile(a.instance/name,out/name)
for name in ('console.log','latest.log','debug.log'):
    if (a.instance/'logs'/name).exists():shutil.copyfile(a.instance/'logs'/name,out/name)
errors=[line for line in (a.instance/'logs/latest.log').read_text(encoding='utf-8',errors='replace').splitlines() if '/ERROR]' in line or '/FATAL]' in line]
dsro_errors=[line for line in errors if any(term in line for term in ('RenderOptimizer','Dragon Survival Render Optimizer','DSRO startup smoke','GPU fallback','dsbr'))]
assessment={'pass':result['pass'] and a.visual_verified and not dsro_errors,
            'kind':'startup-world-smoke','jarSha256':digest(jar),'visualVerified':a.visual_verified,
            'titleReached':result.get('titleReached',False),'worldReached':result.get('worldReached',False),
            'runtimePass':result['pass'],'performanceResampled':False,'dsroErrors':dsro_errors,
            'otherModErrors':errors,'rawResult':'smoke/smoke-result.json'}
(out/'assessment.json').write_text(json.dumps(assessment,ensure_ascii=False,indent=2),encoding='utf-8')
lines=['# DSRO 更名后的启动与世界检查','',f"结果：{'通过' if assessment['pass'] else '未通过'}。检查实际更名 jar，未重复性能采样。",'',
       '进入主菜单和隔离测试世界；第三人称玩家、龙魂及物品栏实体预览均记录截图与渲染统计。实际结果以原始 JSON 为准。',
       f"客户端 SHA-256：`{digest(jar)}`",'',
       '[主菜单](smoke-title.png) · [世界及模型](smoke-world.png) · [物品栏预览](smoke-inventory.png)', '',
       f"DSRO 相关 ERROR/FATAL：{len(dsro_errors)}；整合包其他 ERROR/FATAL 日志：{len(errors)-len(dsro_errors)}，全部列在 assessment.json。此检查不代表整合包所有第三方资源或模组均无问题。",'']
(out/'SUMMARY.md').write_text('\n'.join(lines),encoding='utf-8')
summary_path=project/'validation/alpha4/summary.json';summary=json.loads(summary_path.read_text(encoding='utf-8'))
summary['startupSmoke']=assessment;summary_path.write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:assessment[k] for k in ('pass','titleReached','worldReached','visualVerified','dsroErrors')},ensure_ascii=False))
