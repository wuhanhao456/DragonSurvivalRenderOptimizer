# 正式性能验收流程

基准命令已经实现，正式 27 组测试尚未执行。功能测试 JSON 不能代替这里的性能结果。

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

每次最多保留 100,000 个帧间隔用于分位数；更高帧数采用均匀蓄水池抽样，`frameSamples` 始终记录全部帧数，`quantileSamples` 为实际分位数样本数。纹理归因最多 512 个键，溢出淘汰旧键，但总计数保留。

自动汇总要求九个 `(人数, 重复)` 组合完整、GPU 实际提交过、没有记录功能故障、正常优化模式没有回读、纹理模式预热后不继续生成、顶点提交时间至少下降 50%，GPU P95 相对纹理模式不恶化超过 5%。输出只是这部分数值门槛；仍需核查回退计数和画面，确认没有通过减少内容获得性能。

正式发布另须完成：龙娘/附属龙种、零厚度翅膀、隐藏骨骼、第一/第三人称、物品栏预览、发光、装备/纹饰/Curios、手持物、飞行、桶滚、技能粒子定位；八包阴影、发光、材质与透明回退；反复资源重载、断线、模式切换之后资源不累积。
