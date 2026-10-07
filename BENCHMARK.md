# 正式性能验收流程

alpha.5 的当前范围是用户选择的配对短测，见 [validation/alpha5](validation/alpha5)：15 秒预热、60 秒全帧采样、每景一次，只有触发保护线的场景再复核一次。它不补跑或冒充下述 alpha.3 三轮长测。`tools/run_alpha5_short.py` 接收显式的基线/候选 jar、Java/runtime、冻结整包、测试世界与构建路径；同时冻结 `validationJar`，场景为 1/4/12 玩家、4 末、4 地黄龙、混合 12 NPC、17 龙魂加 4 末。主比较用 Complementary Unbound r5.9，测试驱动修正相机后两版本共用同一驱动。`tools/collect_alpha5.py` 核对 jar/驱动/完整 CSV 摘要并归档相对路径证据，不上传启动参数或账户。

alpha.3／alpha.2 的 Low 帧比较方法见 [LOW_FPS.md](LOW_FPS.md)。当前已保存 9 组 60 秒预热／300 秒完整采样；用户要求停止剩余长测，完整三轮矩阵未完成，结果见 [PERFORMANCE.md](PERFORMANCE.md)。功能测试 JSON 不能代替性能结果。

以下保留游戏内三模式基准和 alpha.2 的历史复现流程；它与 alpha.3 的版本间配对比较不同。

已完成的 1/4/12 条龙短时对照见 [PERFORMANCE.md](PERFORMANCE.md)。复现隔离渲染测试需先构建 `validationJar`，然后运行 `python tools/launch_probe.py --iris --shaderpacks --multi-dragon-benchmark`。它使用离线测试身份和本地合成 `RemotePlayer`，预热 5 秒、采样 15 秒、两轮且第二轮逆序；不是正式多人网络测试。结束后用 `python tools/collect_multi_benchmark.py --instance <临时实例路径>` 保存原始结果和汇总。

1. 在化龙整合包建立固定场景，分别准备 1、4、12 名使用目标渲染器的龙玩家。命令中的人数是场景标签，不会生成玩家，也不自动核验玩家数量。
2. 固定分辨率、视距、相机、光影包、装备、外观、日照和运动脚本；禁用会遮住画面或暂停世界的界面。决定是否限帧/VSync，并保证三模式一致。评估性能收益时建议使用不限帧条件，同时保留用户实际配置的一组对照。
3. 每个场景依次执行以下三次；每条命令自动按 `VANILLA → TEXTURES → GPU` 测量，各预热 60 秒、采样 300 秒，一条约 18 分钟。不要在测试中切换光影、重载资源、改模式、退出世界或暂停；这些操作会中止基准并恢复先前模式。

```text
/dsbr benchmark 1 1
/dsbr benchmark 1 2
/dsbr benchmark 1 3
/dsbr benchmark 4 1
/dsbr benchmark 4 2
/dsbr benchmark 4 3
/dsbr benchmark 12 1
/dsbr benchmark 12 2
/dsbr benchmark 12 3
```

4. 结果写入游戏目录 `logs/dsbr-render-benchmark/<时间>-p<人数>-r<重复>/`。每个模式有独立 JSON，完成三模式后写 `comparison.json`。记录外观/相机/光影和硬件环境的固定条件，另保存同场景配对截图。
5. 汇总：

```powershell
python tools/compare_benchmarks.py '<游戏目录>/logs/dsbr-render-benchmark' --output benchmark-summary.json
```

帧时间来自连续两次渲染帧开始的间隔，包含等待帧率上限的时间；`FRAME_CPU_NANOS` 来自单帧事件区间。`CPU_VERTEX_SUBMIT_NANOS` 计原 `renderCubesOfBone` 提交，`GPU_BONE_SUBMIT_NANOS` 计替代后的姿态记录，`GPU_DRAW_CPU_NANOS` 单独列出分发和绘制的 CPU 成本。CPU 指标是 Java 计时，不是 GPU 时间查询。VRAM 字节是资源尺寸估算，网格预算包含预留和工作缓冲，不是驱动总显存使用量。

普通诊断继续保留旧的 100,000 个间隔蓄水池分位数字段；alpha.3 基准模式增加完整采样（最多 2,000,000 帧），以全样本计算 Low 和分位数，溢出／时长不足会作废。测试驱动的 CSV 保存真实 Minecraft 显示循环纳秒间隔。详细玩家／纹理诊断默认关闭，启用时归因最多 512 个键；轻量阶段计数和故障原因始终保留。

自动汇总要求九个 `(人数, 重复)` 组合完整、GPU 实际提交过、没有记录功能故障、正常优化模式没有回读、纹理模式预热后不继续生成、顶点提交时间至少下降 50%，GPU P95 相对纹理模式不恶化超过 5%。输出只是这部分数值门槛；仍需核查回退计数和画面，确认没有通过减少内容获得性能。

正式发布另须完成：龙娘/附属龙种、零厚度翅膀、隐藏骨骼、第一/第三人称、物品栏预览、发光、装备/纹饰/Curios、手持物、飞行、桶滚、技能粒子定位；八包阴影、发光、材质与透明回退；反复资源重载、断线、模式切换之后资源不累积。
