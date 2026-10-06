"""Write a hardware-scoped report from three complete paired comparisons."""
import argparse, json, pathlib, statistics
from run_low_suite import read

def partial_report(root, hardware):
    rows=read(root/'fixed/runs.json')
    lines=['# alpha.3 Low 帧实测（预发布，未完成正式验收）','',
           '按用户要求停止剩余长测。目前保存 9 组完整采样：alpha.2 / alpha.3 的首轮配对各 1、4、12 龙，以及 alpha.3 第二轮的 1、4、12 龙。每组预热 60 秒、完整采样 300 秒；第二轮 alpha.3 没有再次崩溃。第二轮缺少对应 alpha.2，不能计算三次配对中位数。整包外观、预载飞行、首次区块飞行的正式比较未执行。','',
           f"硬件：{hardware['cpu']} / {hardware['gpu']}，内存 {hardware['physicalMemoryBytes']/2**30:.1f} GiB；{hardware['java']}，Minecraft 1.21.1 / NeoForge 21.1.248。结果只代表当前机器，原 spark 报告机器需要另行实测。",'',
           '固定同种族、成长 40、阶段、缩放、装备、绝对位置和镜头；1280×720、视距 6、ComplementaryReimagined r5.9、不限帧、VSync 关闭。MC 与窗口最终限帧值为 260（不限帧），WGL swap interval = 0。两版 JVM 参数一致：`-Xms1G -Xmx4G`。正式采样关闭 JFR；隔离副本关闭 ModernUI 后台限帧与 spark 后台分析。','',
           'Low FPS = 1000 ÷ 最慢 ceil(N × 比例) 帧时间的平均值（毫秒）；P99.9 为最近秩分位数，与 0.1% Low 含义不同。','',
           '| 龙数 | 版本 / 轮次 | 平均 FPS | 1% Low | 0.1% Low | P95 ms | P99 ms | P99.9 ms |',
           '| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: |']
    for r in sorted(rows,key=lambda x:(x['dragonPlayers'],x['pairRepetition'],x['versionLabel'])):
        m=r['metrics']; values=[m[k] for k in ('averageFps','low1Fps','low01Fps')]+[m['frameTimeMs'][k] for k in ('p95','p99','p999')]
        lines.append(f"| {r['dragonPlayers']} | {r['versionLabel']} / {r['pairRepetition']} | "+' | '.join(f'{v:.2f}' for v in values)+' |')
    lines+=['','首轮配对变化（alpha.3 相对 alpha.2；单次观察，不能代替三轮验收）：','',
            '| 龙数 | 平均 FPS | 1% Low | 0.1% Low | P95 帧时间 | P99 帧时间 |',
            '| ---: | ---: | ---: | ---: | ---: | ---: |']
    for count in (1,4,12):
        pair={r['versionLabel']:r['metrics'] for r in rows if r['dragonPlayers']==count and r['pairRepetition']==1}
        a,b=pair['alpha.2'],pair['alpha.3']
        values=[(b[k]/a[k]-1)*100 for k in ('averageFps','low1Fps','low01Fps')]+[(b['frameTimeMs'][k]/a['frameTimeMs'][k]-1)*100 for k in ('p95','p99')]
        lines.append(f'| {count} | '+' | '.join(f'{v:+.2f}%' for v in values)+' |')
    lines+=['','| 龙数 | 版本 / 轮次 | 完整帧数 | >16.7 ms | >33.3 ms | >50 ms | 最大帧时间 ms |',
            '| ---: | --- | ---: | ---: | ---: | ---: | ---: |']
    for r in rows:
        m=r['metrics']; c=m['longFramesAboveMs']
        lines.append(f"| {r['dragonPlayers']} | {r['versionLabel']} / {r['pairRepetition']} | {m['samples']} | {c['16.7']} | {c['33.3']} | {c['50']} | {m['maximumFrameMs']:.2f} |")
    lines+=['','全部有效固定采样的 `GENERATED = 0`、`READBACK = 0`，GPU 处于启用状态且每帧实体绘制数量符合 1、4、12 龙的检查；没有记录 GPU 后端故障。三个完成的测试客户端清理后纹理与网格／姿态资源均回到 0。资源统计包含姿态池的保守堆估算，不代表驱动总显存。','',
            '八个光影包的纹理／GPU 配对功能检查完成，整包 225 个模组只含 Core 0.10.1。30 个龙娘／洞穴东方龙模型与姿态用例涵盖静止、飞行、第一人称、真实物品栏与隐藏头骨；检查呼吸技能定位骨骼有限且接近玩家。无光影与缺少依赖回退也通过。完整桶滚、实际技能粒子／手持物组合、复杂透明材质画面及反复断线重连压力测试未完成，不能宣称全部视觉场景验收通过。','',
            '候选版第二轮的一次早先运行在 4 龙采样中崩溃：DS `DragonModel.applyMolangQueries` 的动画历史列表平均计算发生 `LinkedList$Node cannot be cast to Double`。没有确认原因，未归因于 DS、JVM、硬件或本优化；该运行作废，原参数重新跑完候选版第二轮后未复现。崩溃报告与状态见 [incident.json](validation/low-frames/incidents/incident.json) 和 [完整报告](validation/low-frames/incidents/20261006-145607-client.txt)。','',
            '另有一次运行因附加只读诊断而作废；用户停止后的下一次 alpha.2 启动未进入有效采样。这些记录保存在 [停止与作废清单](validation/low-frames/run-log.json)。','',
            '独立 JFR 诊断与阶段短测见 [诊断汇总](validation/low-frames/diagnostics/summary.json)。JFR 的分配权重是估计值；诊断候选 jar 早于最终 jar，不作为最终版正式性能结论。','',
            '当前实测 GPU 模式收益最大，继续默认开启；物品栏保持 CPU 预览。alpha.3 作为预发布测试版提供，不宣称三轮正式验收通过或原报告机器获得相同提升。','',
            '完整原始帧间隔（gzip CSV）、SHA-256、计数／耗时／资源和清理结果：[runs.json](validation/low-frames/fixed/runs.json)。[方法与源码实现](LOW_FPS.md)，[证据总索引](validation/low-frames/matrix.json)，[alpha.2 历史三模式短测](PERFORMANCE_ALPHA2.md)。GitHub Release 只附最终客户端 jar，源码与测试结果均在仓库。']
    return '\n'.join(lines)+'\n'

def main():
    p=argparse.ArgumentParser(); p.add_argument('evidence',type=pathlib.Path); p.add_argument('--output',required=True,type=pathlib.Path); p.add_argument('--partial',action='store_true'); a=p.parse_args()
    state=read(a.evidence/'matrix.json'); hardware=read(a.evidence/'hardware.json')
    if a.partial:
        if not state.get('stoppedByUser'): raise ValueError('Partial report requires the user stop record')
        a.output.write_text(partial_report(a.evidence,hardware),encoding='utf-8'); return
    if not state.get('complete'): raise ValueError('Incomplete formal matrix')
    lines=['# alpha.3 Low 帧配对测试', '',
           f"当前机器：{hardware['cpu']}，{hardware['gpu']}；物理内存 {hardware['physicalMemoryBytes']/2**30:.1f} GiB，Java 21。结果仅代表该硬件和场景，原 spark 报告机器需要另行实测。", '',
           '1280×720、视距 6、ComplementaryReimagined r5.9；VSync 与 ModernUI 后台限帧关闭，最终生效上限为 MC 的不限帧值 260。正式运行关闭 spark 后台分析及 JFR。整包堆上限 8 GiB，固定渲染场景 4 GiB；每个 A/B 配对的 JVM 参数相同。', '',
           '每组预热 60 秒、完整采样 300 秒、重复三次，版本顺序 A/B、B/A、A/B。性能变化是三次配对百分比的中位数；表内绝对值是各版本三次测量的中位数。Low = 1000 ÷ 最慢 ceil(N × 比例) 帧的平均毫秒时间，分位数不标为 Low。', '',
           '| 场景 / 龙数 | 版本 | 平均 FPS | 1% Low | 0.1% Low | P95 ms | P99 ms | P99.9 ms |',
           '| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |']
    changes=[]
    names={'fixed':'固定','appearance':'整包外观','flight-new':'整包首次区块飞行','flight-preloaded':'整包预载飞行'}
    for scenario in ('fixed','appearance','flight-preloaded','flight-new'):
        rows=read(a.evidence/scenario/'runs.json'); summary=read(a.evidence/scenario/'summary.json')
        for count in sorted({r['dragonPlayers'] for r in rows}):
            for version in ('alpha.2','alpha.3'):
                metrics=[r['metrics'] for r in rows if r['dragonPlayers']==count and r['versionLabel']==version]
                if len(metrics)!=3: raise ValueError('Missing repetitions')
                values=[statistics.median(m[k] for m in metrics) for k in ('averageFps','low1Fps','low01Fps')]
                values += [statistics.median(m['frameTimeMs'][k] for m in metrics) for k in ('p95','p99','p999')]
                lines.append(f"| {names[scenario]} / {count} | {version} | "+' | '.join(f'{x:.2f}' for x in values)+' |')
        for group in summary['groups']:
            delta=group['medianPairedChangePercent']
            changes.append(f"| {names[scenario]} / {group['dragonPlayers']} | "+' | '.join(f'{delta[k]:+.2f}%' for k in ('averageFps','low1Fps','low01Fps','p95Ms','p99Ms'))+f" | {'通过' if group['pass'] else '未通过'} |")
    lines += ['', '| 场景 / 龙数 | 平均 FPS 变化 | 1% Low 变化 | 0.1% Low 变化 | P95 时间变化 | P99 时间变化 | 门槛 |',
              '| --- | ---: | ---: | ---: | ---: | ---: | --- |', *changes, '',
              f"性能门槛：{'通过' if state['performancePass'] else '未通过，不宣称达到 Low 提升目标，不发布'}。4、12 龙固定场景要求 1% Low ≥ +10%；所有场景的平均 FPS / 0.1% Low ≥ -5%，P95 / P99 时间变化 ≤ +5%。", '',
              '固定场景和外观场景使用同种族、成长 40、相同阶段与缩放、绝对位置及镜头。外观场景每 10 秒切换装备／翅膀，并穿插等内容同步与耐久变化。飞行使用种子 2067、标准世界、开启结构生成，高度 256，沿 +X 以 8 格／秒移动，12 条龙保持编队；预载和首次进入区块分别从各自相同的初始世界副本测试。', '',
              '多龙使用客户端合成 RemotePlayer，覆盖渲染及整包客户端工作，不测真实多人网络或远端服务器负载。全部原始间隔以 frames.csv.gz 保存，runs.json 记录未压缩与压缩 SHA-256、jar／驱动／资源摘要、完整 Low 与分位数、超过 16.7 / 33.3 / 50 ms 的帧数、耗时计数、回退和资源占用。受管资源包含姿态池的保守堆内存估算，不代表驱动总显存。', '',
              '测试方法与复现见 [LOW_FPS.md](LOW_FPS.md)。完整结果见 [validation/low-frames/matrix.json](validation/low-frames/matrix.json)。GitHub Release 仅附最终客户端 jar；源码和证据存放在仓库。']
    a.output.write_text('\n'.join(lines)+'\n',encoding='utf-8')

if __name__=='__main__': main()
