# alpha.3 Low 帧优化与验收

本轮在 alpha.2 的 GPU 路径上减少重复工作，默认 GPU、物品栏 CPU 预览保持不变。没有修改模型精度、动画更新频率或骨骼遍历。正式验收须等待完整 A/B 数据和视觉回归满足门槛；阶段短测及 JFR 不作为正式提升证明。

最新状态：用户要求停止剩余长测，已保存 9 组完整采样及功能检查，并按这一要求提供 alpha.3 **预发布测试版**。没有宣称三次正式配对通过；一次未确认原因的 DS 动画计算崩溃、未执行的场景均在 [PERFORMANCE.md](PERFORMANCE.md) 和 [运行清单](validation/low-frames/run-log.json) 明示。

## 实现

- `AppearanceCache` 按独立 `DragonStateHandler` 和帧缓存实际皮肤字段检查。同步／编辑修订号及时失效，每帧仍检查字段，因此没有调用 dirty 方法的修改也会更新。相同内容复用原有 `AppearanceKey`、SHA-256 摘要与纹理名。
- `ArmorAppearance` 复用每帧结果，跨帧比较可见物品组件；数量、耐久、修复费用不参与外观。实际变化才序列化 NBT、计算摘要，保留 DS 的装备、染色、纹饰、爪牙和 Curios 判定。
- `PosePool` 按骨骼容量复用原生缓冲、矩阵及可见标记。每条命令独占租约，绘制或丢弃后才归还。CPU 紧急回放仍可读取矩阵；保留的原生及保守估算堆内存计入预算。重载和清理释放池。
- compute shader 链接后缓存四个 uniform 位置；按渲染器类缓存几何接口检查。相同刷新源／RenderType 的命令共用一次 GL 绑定保存，保留原批次顺序和 shader 选择。缓冲上传方式没有更换。
- 详细玩家／纹理归因默认关闭。轻量阶段计数、耗时和故障原因保留；原命令与 JSON 字段保持兼容。

## 采样定义

测试驱动通过仅在 `validationJar` 中的 Minecraft `runTick(boolean)` 注入，记录连续显示循环的真实间隔，包括 swap、驱动等待和限帧等待。采样保存全部原始纳秒间隔；序列不连续、溢出、无样本或时长不足均作废。驱动不进入客户端发布 jar。

`1% Low = 1000 / 最慢 ceil(N × 0.01) 帧的平均毫秒时间`；0.1% 使用 `0.001`。P50/P95/P99/P99.9 使用向上取整的最近秩分位数。报告同时列出超过 16.7、33.3、50 ms 的帧数，普通分位数不称为 Low。

游戏内基准的 `completeFrameCapture` 也保留完整样本和 Low，跳过开始采样前的跨界间隔。不完整采样会中止该次基准。普通诊断中的旧蓄水池分位数字段继续存在，完整基准的分位数使用全样本。

## 复现

先构建 `build validationJar fallbackValidationJar glCheck`，保存正式 alpha.2 jar 和候选 alpha.3 jar 的 SHA-256。两个版本使用同一验证驱动。

```powershell
python tools/prepare_low_fixture.py --pack '<化龙整包目录>' --output '<新的本地固定资源目录>'
python tools/run_low_suite.py --baseline '<alpha.2.jar>' --candidate '<alpha.3.jar>' --pack '<固定资源目录>' --group-counts --output '<固定多龙结果目录>'
python tools/run_low_suite.py --baseline '<alpha.2.jar>' --candidate '<alpha.3.jar>' --pack '<固定资源目录>' --fullpack --counts 12 --scenario appearance --output '<外观结果目录>'
```

固定资源目录只冻结 Git 记录的模组、配置、KubeJS、Hotai 和光影包，不复制已有存档或启动器账户。每次启动验证文件集合和 SHA-256。整包副本仅保留 Core 0.10.1；测试副本关闭模板迁移、LockDown 维度锁定／登录传送。同步创建世界期间，测试驱动处理整包排队的客户端资源重载任务；进入采样后不执行该辅助逻辑。

正式测试固定当前机器、1280×720、视距 6、ComplementaryReimagined r5.9、VSync 关闭且不限帧。隔离副本关闭 ModernUI 的后台限帧及 spark 后台分析；原整包配置保留。驱动核验实际窗口上限、最终生效上限均为 260（MC 中表示不限帧）及 WGL 交换间隔 0。整包堆上限 8 GiB；各 A/B 场景的 JVM 参数相同。

每组预热 60 秒、采样 300 秒，重复三次，版本顺序交替。固定场景使用绝对锚点 `(0.5, -60, 0.5)`，相同成长 40、阶段、尺寸、装备和镜头。外观场景每 10 秒切换装备／翅膀，加入等内容同步及耐久变化。

移动场景使用种子 2067、标准世界并开启结构生成，固定高度 256，沿 +X 以 8 格／秒飞行，所有合成龙保持相对编队。先用 `launch_probe.py --fullpack --multi-dragon-benchmark --gpu-only --scenario flight-new --prepare-world --sample 300` 准备初始世界，退出完成存盘后，用 `--world-template '<该隔离实例>/saves/multi-render-validation'` 为两版本分别复制新副本。预载路线单独使用 `flight-preloaded --prepare-world`；两组世界不能混用。现有玩家存档不进入测试。

合成 `RemotePlayer` 测试覆盖客户端多龙渲染，不测真实多人网络或服务器负载。采样前后截图、渲染计数、资源清理结果、原始 CSV 和 jar／驱动／资源摘要随测试记录保存。

## 发布门槛

4、12 龙固定场景的三次配对变化中位数要求 1% Low 至少 +10%。所有正式场景的 0.1% Low、平均 FPS 不低于 -5%，P95/P99 时间增幅不超过 5%。静态纹理生成和正常 CPU 回读为零，预期 GPU 命令完成，无新视觉错误／故障，清理后资源回到 0。

JFR 使用独立运行，分析录制帧时间窗口内的分配样本和 GC 暂停；分配权重是估计值。JFR 运行不进入性能验收。当前硬件是 i7-13700K、RTX 5070 Ti、驱动 596.49；原 spark 报告机器需要另行实测。

未满足完整门槛不得宣称正式验收通过。按用户后续停止测试要求，alpha.3 可作为明确注明验收不完整及已知事件的预发布测试版提供；`package_release.py --incomplete-prerelease` 只在存在该候选的停止记录和归档数据时打包，manifest 的 `formalAcceptanceComplete` 保持 false。完整验收流程不使用此选项。GitHub Release 附件仅最终客户端 jar；源码、兼容与结果放仓库。
