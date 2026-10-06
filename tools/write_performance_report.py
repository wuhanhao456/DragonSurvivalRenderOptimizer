"""Write the short comparison from archived measurements of the delivery jar."""
import json, pathlib

project = pathlib.Path(__file__).resolve().parents[1]
summary = json.loads((project / 'validation/multi-dragon-iris/summary.json').read_text(encoding='utf-8'))
validation = json.loads((project / 'validation/summary.json').read_text(encoding='utf-8'))
assert summary['testedJarSha256'] == validation['testedJarSha256'], 'Benchmark and delivery validation differ'
groups = {(r['dragonPlayers'], r['mode']): r for r in summary['groups']}
text = ['# 同场景多龙短时渲染对照', '',
        '2026-10-06，DSBR 0.2.0-alpha.2。最终客户端 jar 与功能检查使用相同 SHA-256：', '',
        '`' + summary['testedJarSha256'] + '`', '',
        '当前机器和场景中，GPU 模式在 1、4、12 条龙下都最快，因此新配置默认 GPU。物品栏预览使用原 CPU 几何提交，继续保留纹理优化，世界阶段仍用 GPU。', '',
        '## 平均帧率', '', '| 同屏龙数 | 原渲染 FPS | 仅纹理优化 FPS | GPU FPS | GPU 相对原渲染 |',
        '| --- | ---: | ---: | ---: | ---: |']
for n in (1, 4, 12):
    a, b, c = (groups[n, m] for m in ('VANILLA', 'TEXTURES', 'GPU'))
    text.append(f"| {n} | {a['averageFps']:.2f} | {b['averageFps']:.2f} | {c['averageFps']:.2f} | {(c['averageFps']/a['averageFps']-1)*100:+.1f}% |")
text += ['', '## 帧时间', '', '每项为两次独立采样的分位数均值，不是把两次帧样本合并后重新计算的分位数。单位毫秒，越小越好。', '',
         '| 龙数 | 模式 | P50 | P95 | P99 |', '| --- | --- | ---: | ---: | ---: |']
for r in summary['groups']:
    text.append(f"| {r['dragonPlayers']} | {r['mode']} | {r['meanRunP50Ms']:.3f} | {r['meanRunP95Ms']:.3f} | {r['meanRunP99Ms']:.3f} |")
text += ['', '## 提交成本与资源', '',
         '| 龙数 | 原 CPU 顶点提交 ms/帧 | GPU 模式 CPU 顶点提交 | GPU 姿态记录 ms/帧 | GPU 分发/绘制 CPU ms/帧 | 纹理峰值 MiB | GPU 受管资源峰值 MiB |',
         '| --- | ---: | ---: | ---: | ---: | ---: | ---: |']
for n in (1, 4, 12):
    a, c = groups[n, 'VANILLA'], groups[n, 'GPU']
    text.append(f"| {n} | {a['cpuVertexSubmitMsPerFrame']:.3f} | {c['cpuVertexSubmitMsPerFrame']:.3f} | {c['gpuPoseCaptureMsPerFrame']:.3f} | {c['gpuDrawCpuMsPerFrame']:.3f} | {c['peakTextureBytes']/1048576:.2f} | {c['peakGpuResourceBytes']/1048576:.2f} |")
text += ['', '这些是 Java CPU 计时与受管缓冲尺寸估算，不是 GPU 耗时查询或驱动总显存。GPU 姿态记录包含骨骼遍历，不能再与 `GPU_BONE_SUBMIT_NANOS` 相加。', '',
         '## 方法与范围', '',
         '- Intel Core i7-13700K、RTX 5070 Ti、驱动 596.49；Java 21，MC 1.21.1，NeoForge 21.1.248，DS 2.0.71，GeckoLib 4.9.3，Iris 1.8.14-beta.1，Sodium 0.8.13。',
         '- ComplementaryReimagined_r5.9，1280×720，视距 6，VSync 关闭、不限帧；MC 的 260 配置值在该版本表示不限帧。',
         '- 固定前方第三人称镜头、同一平坦世界种子、日照、同种龙/模型/皮革盔甲。每组同步检查所有角色成长值 40、同阶段、同缩放 0.95；停止成长，避免不同尺寸和外观变化干扰。',
         '- 本地玩家加客户端合成 RemotePlayer，共 1/4/12 条。每次先预热 5 秒再采样 15 秒，两轮；第二轮 GPU → TEXTURES → VANILLA，与第一轮相反。平均 FPS 按总帧数/总采样时间计算。',
         '- GPU 样本要求每帧实体提交至少为龙数的 1.8 倍，确认本体/盔甲没有只绘制第一条龙；每组没有记录后端故障，优化路径正常纹理回读为零，结束后受管资源清零。', '',
         '静止外观预热后，原渲染也没有继续生成纹理；此场景没有复现 spark 报告中的持续纹理维护瓶颈。仅纹理模式需要计算内容键，在这里额外增加 CPU 成本，不能从这组结果推出它总比原版快。GPU 的收益主要来自减少逐顶点 CPU 提交。', '',
         '这是隔离实例的短时渲染对照，不测真实多人的网络/服务器负载，未装入完整化龙 Core 和全部整合包模组，也未完成每组 60 秒预热 + 300 秒采样、三重复的正式验收。其他显卡、光影包和复杂动作的收益需另测。', '',
         '原始数据与校验：[summary.json](validation/multi-dragon-iris/summary.json)、[multi-comparison.json](validation/multi-dragon-iris/multi-comparison.json)、[multi-result.json](validation/multi-dragon-iris/multi-result.json)。截图保留在本地工程 validation 目录；GitHub Release 附件仅为最终客户端 jar。复现步骤见 [BENCHMARK.md](BENCHMARK.md)。', '']
(project / 'PERFORMANCE.md').write_text('\n'.join(text), encoding='utf-8')
print('Wrote PERFORMANCE.md from delivery-jar measurements')
