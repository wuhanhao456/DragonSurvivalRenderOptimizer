# Dragon Survival Render Optimizer — DSRO

当前版本 **0.2.0-alpha.5**。原名 Dragon Survival Bedrock Renderer（DSBR），现为 Dragon Survival Render Optimizer（DSRO）。兼容 mod ID `dsbr`，默认开启 GPU 和纹理缓存。面向 Minecraft 1.21.1、Java 21、NeoForge 21.1.248、Dragon Survival 2.0.71 和 GeckoLib 4.9.3。

新增可选 Core 0.10.1 的末、地黄龙 GPU 适配，玩家/NPC 相邻合批、批次姿态一次上传，以及 CPU 回退临时存储复用。**Core 不是必需依赖**，只有龙生及其前置也能使用。未知 Core 版本仅关闭 NPC 适配，不影响龙生路径。保留原动画、渲染层、物品栏 CPU 预览和技能骨骼计算。

## 配置与操作

NeoForge 模组配置界面只有一份有效配置：`config/dsbr-optimizer-client.toml`。八项设置均有中英文说明，旧配置键和值继续有效：

| 设置 | 默认值 | 用途 |
| --- | --- | --- |
| `mode` | `GPU` | GPU 与纹理优化；`TEXTURES` 仅纹理缓存；`VANILLA` 原版渲染 |
| `textureBudgetMiB` | 64 | 纹理缓存软预算，当前帧在用纹理受保护 |
| `meshBudgetMiB` | 128 | 网格、独立姿态和 GPU 工作缓冲预算 |
| `unusedTextureSeconds` | 30 | 闲置纹理保留时长，预算可能提前触发清理 |
| `traceDirtyFlags` | false | 最多 32 条皮肤失效调用栈，仅排查时开启 |
| `detailedDiagnostics` | false | 玩家和纹理归因，仅排查时开启 |
| `beloongNpcGpu` | true | GPU 模式下启用已验证的可选 NPC 适配，缺少 Core 时自然不生效 |
| `reuseCpuVertexScratch` | true | GPU 模式下复用标准 CPU 回退路径的临时矩阵和向量，包括物品栏 |

`/dsbr` 显示启用状态与兼容检测；`vanilla`、`textures`、`gpu` 切换模式；`stats`、`reset`、`export` 查看、重置、导出统计。保留 `/beloongrender` 别名及玩家基准命令，详见 [BENCHMARK.md](BENCHMARK.md)。不支持的能力、材质、几何或消费器继续原 CPU 路径；物品栏预览保持 CPU 几何提交。

## 更新与龙魂优化

发布时用 `dsro-0.2.0-alpha.5.jar` 替换旧 DSBR／DSRO，同一实例保留一份。旧 jar 放在 `mods` 外可回退。GitHub Release 只附最终客户端 jar，源码和测试数据留在仓库。`dsbr-optimizer-client.toml` 与 `/dsbr` 沿用兼容名称。

已移除旧 Bedrock/YSM 引擎、旧菜单、旧接管配置和 `/dsbr legacy`。`dsbr-client.toml` 不再加载。不支持 DS 2.0.67 的旧接管路径。

龙魂沿用 DS 的动画控制器和 GeckoLib 的逐帧骨骼计算。相同外观共享不可变合成纹理；动画存在性查询按资源与动作缓存，重载失效。DS 的假玩家活跃登记、外观同步、朝向、比例和指示底座仍由原渲染器处理。

在同一缓冲源和渲染阶段中，连续且兼容的龙魂按网格、材质、Iris 属性和发光状态合批。每个实例保留独立姿态、颜色、光照和覆盖值，计算着色器一次转换多个实例，再一次绘制。透明排序材质保持 CPU；能力限制或预算触发拆批。计算故障按原顺序回放已捕获姿态。新增 `SOUL_RENDER`、`SOUL_GPU_PASS`、`SOUL_GPU_DRAW_CALLS`、`SOUL_TEXTURE_HIT` 和 `SOUL_CPU_FALLBACK` 统计，区分实例提交与实际绘制次数。

## 构建与验证

网络盘可直接用 Java 启动 Gradle wrapper，并通过 `-PdsbrBuildDir=本地目录` 指定输出，避免 Windows 批处理的 UNC 当前目录限制。标准任务为 `test glCheck jar sourcesJar validationJar`，测试驱动不进入发布 jar。

alpha.5 采用隔离实例，alpha.4/候选 GPU 模式配对短测：每景预热 15 秒、采样 60 秒、一次比较，按场景交替版本顺序。测试使用完整显示循环间隔，Low 是最慢对应比例帧时间的均值取倒数，尾部样本向上取整。主比较使用 Complementary Unbound r5.9、1280×720、视距 6、关闭垂直同步/帧率限制，整包堆上限 8 GiB。记录见 [validation/alpha5](validation/alpha5)；最终客户端运行范围和已知限制见 [RUNTIME.md](validation/alpha5/RUNTIME.md)。不将单轮短测称为正式 Low 验收，不代表原报告机器的收益。

未显示收益的同帧动画复用已移出编译源集与配置，保留 [实验代码和原因](experiments/frame-animations)。FancyMenu 和 KubeJS 保持原样，单独分析其成本，不由 DSRO 修改整包配置。

### alpha.4 历史验证

alpha.4 按用户当时要求只测新版一次：无光影与 Complementary 的 1／4／12 个龙魂（每景预热 5 秒、采样 15 秒），并检查配置、连续动画和生命周期。没有旧版对照，不宣称性能提升百分比。证据见 [validation/alpha4](validation/alpha4)。历史 alpha.2／alpha.3 报告保留在 [PERFORMANCE.md](PERFORMANCE.md)。

当时结果：16 项 Java 测试与真实 OpenGL 的独立多姿态／Iris 顶点校验通过；17 个客户端场景执行完毕，龙魂合批生效，九个动作阶段持续采样的龙魂骨骼和时间均推进，吐息及恢复有可见模型证据，清理后资源为零。测试相机受到 DS 钩子覆盖，普通玩家待机／行走／飞行缺少样本，创造模式页也没有实体预览；完整动画和 GUI 预览确认未通过。帧时间只归档，不用于性能结论。原始失败结果保留。详见 [单轮报告](validation/alpha4/SUMMARY.md)。

本轮验证和截图采于更名前。更名后的发布 jar 逐文件核对，只变更显示名称与日志文字，渲染方法指令和其他资源相同；详见 [更名核对](validation/alpha4/branding-verification.json)。后续按要求追加实际更名 jar 的 [启动／世界检查](validation/alpha4/smoke/SUMMARY.md)：主菜单、世界、可见玩家及龙魂、玩家待机动画和物品栏 CPU 实体预览通过，未发现 DSRO 相关 ERROR/FATAL；整合包其他配方／资源报错单独记录。没有重复性能采样或原 17 个场景。

兼容边界见 [COMPATIBILITY.md](COMPATIBILITY.md)。源码 MIT，许可见 `LICENSE` 与 `THIRD_PARTY_NOTICES.md`。
