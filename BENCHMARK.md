# 正式性能验收流程

alpha.5 的当前范围是用户选择的配对短测，见 [validation/alpha5](validation/alpha5)：15 秒预热、60 秒全帧采样、每景一次，只有触发保护线的场景再复核一次。它不补跑或冒充下述 alpha.3 三轮长测。`tools/run_alpha5_short.py` 接收显式的基线/候选 jar、Java/runtime、冻结整包、测试世界与构建路径；同时冻结 `validationJar`，场景为 1/4/12 玩家、4 末、4 地黄龙、混合 12 NPC、17 龙魂加 4 末。主比较用 Complementary Unbound r5.9，测试驱动修正相机后两版本共用同一驱动。`tools/collect_alpha5.py` 核对 jar/驱动/完整 CSV 摘要并归档相对路径证据，不上传启动参数或账户。

alpha.3／alpha.2 的 Low 帧比较方法见 [LOW_FPS.md](LOW_FPS.md)。当前已保存 9 组 60 秒预热／300 秒完整采样；用户要求停止剩余长测，完整三轮矩阵未完成，结果见 [PERFORMANCE.md](PERFORMANCE.md)。功能测试 JSON 不能代替性能结果。

游戏内 `/dsbr benchmark` 三模式基准已移除；性能对比只用下面隔离实例中的测试驱动完成。

已完成的 1/4/12 条龙短时对照见 [PERFORMANCE.md](PERFORMANCE.md)。复现隔离渲染测试需先构建 `validationJar`，然后运行 `python tools/launch_probe.py --iris --shaderpacks --multi-dragon-benchmark`。它使用离线测试身份和本地合成 `RemotePlayer`，预热 5 秒、采样 15 秒、两轮且第二轮逆序；不是正式多人网络测试。结束后用 `python tools/collect_multi_benchmark.py --instance <临时实例路径>` 保存原始结果和汇总。

`/dsbr stats` 与 `/dsbr export` 中的帧时间来自连续两次渲染帧开始的间隔，包含等待帧率上限的时间；`FRAME_CPU_NANOS` 来自单帧事件区间。`CPU_VERTEX_SUBMIT_NANOS` 计原 `renderCubesOfBone` 提交，`GPU_BONE_SUBMIT_NANOS` 计替代后的逐骨姿态记录（自 0.2.3-beta.1 起只在 `detailedDiagnostics` 开启时记录，避免每根骨骼两次计时），`GPU_DRAW_CPU_NANOS` 单独列出分发和绘制的 CPU 成本。CPU 指标是 Java 计时，不是 GPU 时间查询。VRAM 字节是资源尺寸估算，网格预算包含预留和工作缓冲，不是驱动总显存使用量。

普通诊断使用 100,000 个间隔的蓄水池分位数；完整采样与 Low 只由测试驱动的 CSV 计算，它保存真实 Minecraft 显示循环纳秒间隔。详细玩家／纹理诊断默认关闭，启用时归因最多 512 个键；轻量阶段计数和故障原因始终保留。

正式发布另须完成：龙娘/附属龙种、零厚度翅膀、隐藏骨骼、第一/第三人称、物品栏预览、发光、装备/纹饰/Curios、手持物、飞行、桶滚、技能粒子定位；八包阴影、发光、材质与透明回退；反复资源重载、断线、模式切换之后资源不累积。

## 发布打包

`python tools/package_release.py --build <构建目录>` 读取 `gradle.properties` 中的版本，不再写死版本号。正式版本默认要求 `validation/<版本>/` 下有针对同一 jar 的完整验收证据（`summary.json`、`runtime/result.json`、`benchmark/suite.json` 与 `gl-results.json`）。只有单元测试和 `gradlew glCheck` 结果时，可加 `--without-runtime-evidence` 打包，manifest 会记录 `releaseAcceptanceComplete: false` 和 `basicChecks`。预发布版本（版本号含 `-`）不要求正式证据；若 `validation/<版本>/gl-results.json` 存在，manifest 同样记录单元测试与 OpenGL 检查。

## 重渲染姿态复用配对短测

`python tools/run_rerender_pair.py --build <构建目录> --pack <冻结整包> --output validation/<版本>/rerender --verify` 在同一 jar、同一组默认 GPU 场景（1 玩家、12 玩家、17 龙魂 + 4 末 + 4 地黄龙）下，分别以 `--stage 4`（关闭复用）和 `--stage 5`（发布默认）各跑一次，无光影与 Complementary Unbound r5.9 各一组，每景预热 15 秒、采样 60 秒。平均 FPS 与 1% Low 任一退步超过 5% 即判失败。`--verify` 追加一次 stage 5 运行：命中复用时仍照常遍历骨骼，逐骨比较遍历结果与复用的字节，`RERENDER_POSE_MISMATCH` 必须为 0。截图不用于逐像素比较，因为两次运行的动画时间不同。
