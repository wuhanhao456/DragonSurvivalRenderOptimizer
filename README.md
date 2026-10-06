# Dragon Survival Bedrock Renderer

DSBR 的新版本，合入化龙客户端纹理优化与实验 GPU 后端，`dsbr`，Minecraft 1.21.1 / Java 21 / NeoForge 21.1.248。当前版本 **0.2.0-alpha.3（预发布测试版）**，默认开启 GPU 与纹理优化；物品栏继续 CPU 预览，不支持的能力或路径自动回退。

alpha.3 复用外观摘要与姿态矩阵，缓存 shader uniform／渲染器检查，并合并同批次 GL 状态保存。当前机器首轮 GPU 对照中，4／12 龙的 1% Low 分别约 +50%／+54%，候选版复测接近；按用户要求停止剩余长测，**没有完成三轮正式验收**。一次作废运行在 DS 动画历史平均计算处发生类型转换崩溃，原因未确认，原参数重跑未复现；详情与原始数据见 [性能结果](PERFORMANCE.md)。

## 安装和操作

将 `dist/dsbr-0.2.0-alpha.3.jar` 放入客户端 `mods`。不需要装到服务器，不增加网络协议，不修改存档。首版纹理补丁要求 Dragon Survival 2.0.71 和 GeckoLib 4.9.3；GPU 与光影同时使用时要求 Iris 1.8.14-beta.1+mc1.21.1、Sodium 0.8.13+mc1.21.1。版本或目标方法不匹配时关闭补丁，并在日志和命令中说明。详见 [兼容清单](COMPATIBILITY.md)。

客户端命令：

| 命令 | 用途 |
| --- | --- |
| `/dsbr` | 显示模式、启用状态和兼容检测 |
| `/dsbr vanilla` | 恢复 DS 原纹理生成与 CPU 顶点提交 |
| `/dsbr textures` | 仅纹理优化模式 |
| `/dsbr gpu` | 默认模式：纹理优化 + GPU，要求 OpenGL 4.3 |
| `/dsbr stats` | 计数、帧时间分位数、资源估算和回退原因 |
| `/dsbr reset` | 重置统计计数和帧样本，保留故障原因 |
| `/dsbr export` | 写出游戏目录 `logs/dsbr-render-stats.json` |
| `/dsbr benchmark 1 1` | 固定场景，按三种模式各预热 60 秒、采样 300 秒 |
| `/dsbr benchmark` / `benchmark stop` | 基准进度 / 停止并恢复原模式 |

配置文件为 `config/dsbr-optimizer-client.toml`，也可从 NeoForge 模组配置界面编辑：`mode = "GPU"`、`textureBudgetMiB = 64`、`meshBudgetMiB = 128`、`unusedTextureSeconds = 30`、`traceDirtyFlags = false`、`detailedDiagnostics = false`。详细玩家／纹理诊断默认关闭；开启 `traceDirtyFlags`可保存最多 32 条皮肤同步/失效调用栈。运行中严重异常会停用相关优化；修复原因后重启客户端，避免反复尝试失败的 GPU 功能。

## 从旧版更新

下载 [0.2.0-alpha.3 发布包](https://github.com/wuhanhao456/DragonSurvivalBedrockRenderer/releases/tag/0.2.0-alpha.3)，用 `dsbr-0.2.0-alpha.3.jar` 替换客户端中旧的 `dsbr` jar，并移除独立原型 `beloong_render_optimizer` jar。同一实例只留一个 DSBR；保留旧 jar 在 `mods` 之外可回退。新版本仍使用 mod ID `dsbr`，不需要同时安装第二个优化 mod。

旧 `config/dsbr-client.toml` 保留原有 Bedrock/YSM 设置；新增 `general.legacy_backend_enabled = false`，旧配置中的 `normal_render_mode = "BEDROCK"` 不会自动接管新优化模式。新优化配置单独位于 `config/dsbr-optimizer-client.toml`。NeoForge 配置界面可以编辑两个配置。已有 `dsbr-optimizer-client.toml` 中保存的模式继续有效；要将既有实例改为 GPU，设置 `mode = "GPU"` 或执行 `/dsbr gpu`。

旧 Bedrock/YSM 源码保留为显式兼容选项：仅在 DS 2.0.67 且旧桥接接口存在时可用 `/dsbr legacy` 启用；旧路径没有进行本次完整运行回归。DS 2.0.71 上关闭旧接管，使用 DS/GeckoLib 原动画和本次优化路径。`/dsbr vanilla`、`textures`、`gpu` 都关闭旧接管；只有纹理模式加显式 legacy 开关才可能走旧路径。历史引擎说明见 [实现说明.md](实现说明.md)。

## 实现

纹理键包含种族、身体、模型、阶段、纹理尺寸和每一层的实际设置；盔甲沿用 DS 的合成与装备判定，补充可见装备组件、染色、Curios 内容等，排除耐久和维修费用。同内容同步后恢复当前 `SkinData` 编译标记。只在渲染需要时准备纹理，替换 DS 每帧遍历所有玩家的生成/清理逻辑。纹理名携带内容摘要，避免覆盖尚待绘制的旧外观。

保留 DS 原合成 shader，复用按尺寸分组的 framebuffer，直接从 framebuffer 复制到 GPU 纹理。`GpuComposedTexture` 保留 `DynamicTexture` 接口，只有编辑器、导出等明确调用 `getPixels()` 时才延迟回读；显式像素上传仍可用。未使用纹理保留 30 秒，LRU 控制预算。当前帧在用纹理受保护，因此当前可见工作集超过预算时会暂时超过纹理软预算，随后清理未使用内容。

GPU 后端从 GeckoLib 烘焙模型缓存几何，原有动画、骨骼遍历、矩阵跟踪和渲染层照常执行。`renderCubesOfBone` 只保存独立姿态快照，计算着色器变换顶点；待原 `MultiBufferSource` / Iris 批次刷新时，用该阶段实体 shader 绘制。本体、盔甲与 DS 加法发光层可进入 GPU；其他需要排序的透明材质、特殊消费者和自定义立方体实现继续 CPU 渲染。Iris 扩展顶点布局为经检查的 54 字节，包括实体编号、中点 UV、法线和切线；阴影批次沿用 Iris 排序和 shader。

网格、待绘制姿态和工作缓冲参与预算计算；提交前预留容量，网格有批次租约，绘制后释放。重载、模式切换、退出世界、断线时统一清理。同一个 `dsbr` jar 统一协调后端，GPU 模式关闭旧 Bedrock/YSM 接管；发现独立原型 `beloong_render_optimizer` 时停用集成补丁，避免重复注入。没有 JNI、自带本地库或新的动画引擎。

GPU 仅在世界渲染阶段接管几何。物品栏及其他界面预览继续原 CPU 提交，保留纹理优化，避免延迟绘制使用已恢复的界面矩阵和光照。`GUI_CPU_PASS` 统计这类预览，它不表示后端故障。Iris 相等的渲染类型按其状态合并，同一批次仍保存每条龙的独立姿态；不同刷新源保持独立。

## 构建与测试

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
./gradlew.bat test jar sourcesJar
./gradlew.bat glCheck
```

`glCheck` 需要本机 OpenGL 4.3，创建隐藏 GLFW 测试窗口。网络盘构建可指定本地输出，避免压缩/转换工具在网络盘上逐字节写文件：

```powershell
./gradlew.bat test glCheck jar sourcesJar validationJar '-PdsbrBuildDir=C:/Users/wu949/AppData/Local/Temp/DSBR-build'
```

验证驱动是独立 `validationJar`，**不包含在客户端发布 jar 中**。`tools/launch_probe.py` 只创建新的临时实例和测试存档，使用离线测试身份；需要已有合法安装的 libraries/assets、展开后的 NeoForge 启动 JSON 和客户端 jar，路径可用 `--runtime`、`--manifest`、`--game-jar`、`--pack`、`--java`、`--build` 指定。`--iris --shaderpacks` 会读取指定整合包目录中的八个光影包。该工具不会读取账号文件或现有存档。

已执行的检查、配对截图和验收缺口见 [验证记录](VALIDATION.md)；alpha.2／alpha.3 GPU 对照与候选版复测见 [性能结果](PERFORMANCE.md)，实现和复现步骤见 [LOW_FPS.md](LOW_FPS.md)，旧三模式命令见 [基准流程](BENCHMARK.md)。**目前是可安装预发布测试版，尚未达到整合包正式发布验收。** 原始全帧数据、硬件和运行记录在 [validation/low-frames](validation/low-frames)。GitHub Release 只附客户端 jar；源码、兼容清单与测试结果均在仓库。

主命令为 `/dsbr`，保留 `/beloongrender` 别名。

源码 MIT；参考来源和外部依赖见 [第三方说明](THIRD_PARTY_NOTICES.md)。
