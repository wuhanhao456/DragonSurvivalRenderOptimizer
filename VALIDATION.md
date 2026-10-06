# 实现与验证记录

当前交付为 **0.2.0-alpha.3 预发布测试版**，默认 GPU、物品栏 CPU。最终客户端 SHA-256：`fd6a3b321d19b30900d80cf0f9682ae30fb71dee24b36c19f5757b40bd9140ef`。完整记录索引见 [validation/low-frames/matrix.json](validation/low-frames/matrix.json)，原始全帧采样与对照见 [PERFORMANCE.md](PERFORMANCE.md)。

| alpha.3 检查 | 实际结果 |
| --- | --- |
| 构建／工具 | 最终源码 build 成功；14 项 Java 单元检查、15 项 Python 工具检查通过，实际 OpenGL 顶点／纹理／批次异常状态恢复检查通过 |
| 长测数据 | 9 组完整采样，每组 60 秒预热、300 秒记录；首轮 alpha.2／alpha.3 各 1／4／12 龙，候选版第二轮 1／4／12 龙 |
| 固定场景正确性 | 相同成长、阶段、缩放、位置、镜头和光影；纹理生成 0、正常回读 0、预期 GPU 绘制存在、完成客户端清理归零 |
| 纹理行为 | 等内容同步／耐久／数量／修复费用不重新生成；未调用 dirty hook 的实际眼睛层改色／发光更新一次，盔甲染色更新一次，恢复旧皮肤缓存命中 |
| 无光影／依赖 | 最终 jar 无光影路径、真实龙物品栏三模式、缺少 DS／GeckoLib 的安全停用检查通过 |
| 整包八光影 | 225 个模组、Core 0.10.1，八包 TEXTURES／GPU 配对、发光／装备、切换与重载功能检查通过；复杂材质／透明全场景没有穷尽 |
| 模型／姿态 | tundra 与 aether 龙娘、cave／east 龙的静止、飞行、第一人称、物品栏、隐藏头骨 × 两模式，共 30 项；呼吸定位骨骼有限且接近玩家 |
| JFR | 独立诊断运行，仅作为分配／GC 定位；诊断候选早于最终 jar，不纳入正式帧时间比较 |
| 未完成 | 用户要求停止剩余测试：三次完整配对、整包外观／移动正式对照、反复断线重连、完整桶滚／技能粒子／手持物组合 |
| 崩溃事件 | 候选第二轮的早先尝试在 DS 动画历史平均处发生类型转换崩溃，原因未确认；该运行作废，候选原参数复测完成后未复现。原始报告保留 |

这次预发布依据用户停止测试的最新要求提供；**没有通过完整正式验收**。明细见 [运行清单](validation/low-frames/run-log.json)、[事件](validation/low-frames/incidents/incident.json) 和 [功能结果](validation/low-frames/functional)。保留 alpha.2 可供回退。

## alpha.2 历史记录

以下是 0.2.0-alpha.2 的既有检查，不能当作 alpha.3 的本次长测结果。全新配置启动见 [validation/default-gpu-summary.json](validation/default-gpu-summary.json)，历史短测见 [PERFORMANCE_ALPHA2.md](PERFORMANCE_ALPHA2.md)。

日期：2026-10-06。基础验证版本：0.2.0-alpha.2。完整结果索引见 [validation/summary.json](validation/summary.json)。

## 环境与检查

测试机器为 Intel Core i7-13700K / NVIDIA RTX 5070 Ti / NVIDIA 596.49，Java 21、Minecraft 1.21.1、NeoForge 21.1.248。原 spark 报告使用另一台机器；本次检查不代表报告机器的实际性能收益。

| 检查 | 已执行内容 |
| --- | --- |
| 构建 | 客户端 jar、sources jar；验证驱动另行构建，不进入发布 jar |
| 源码构建 | 本次源码直接构建并测试最终 jar；`source-rebuild.json` 保留 0.2.0-alpha.1 的独立源码包重建历史，不能作为本次 jar 的重建证明 |
| Java 单元测试 | 10 项：LRU/租约/30 秒保留、内容键变化、独立姿态、实际 DS 注入契约及签名不匹配、统计快照和有界分位数；新增相等 Iris 类型刷新全部姿态、不同刷新源隔离的批次回归 |
| 基准汇总工具 | 4 项人工数据测试：完整/缺失矩阵、GPU 未执行、持续生成/回读、场景分辨率改变；不作为实机测量 |
| 实际 OpenGL | 隐藏 4.3 上下文；GPU 纹理复制、延迟回读失效、显式上传与图像所有权、重复关闭；GL error = 0 |
| 计算顶点 | 129 个 quad、两根独立骨骼、非均匀/负缩放、旋转、隐藏骨骼、零厚度法线；36/54 字节输出与 Java 参考比较，包含未按 4 字节对齐的 Iris 顶点边界 |
| DS 客户端 | 全新隔离平坦世界；相同皮肤同步、耐久、镜头转动、皮肤变色、盔甲染色、恢复旧皮肤、GPU 本体/盔甲/发光、资源重载、三模式切换、统一清理 |
| 可选依赖回退 | 不安装 DS/GeckoLib 时可启动至标题界面，补丁关闭且清理可执行 |
| 旧配置升级 | 保留旧 `normal_render_mode = "BEDROCK"`；即使显式 legacy 标记为 true，DS 2.0.71 的旧接口不兼容时也不接管；mod ID 仍为 `dsbr` |
| Iris | 八个现有光影包切换；校验当前包名与真实启用状态，捕获纹理/GPU 配对画面与阶段统计 |
| 默认模式 | 无光影及 Iris/Complementary 全新配置，不执行模式命令即可 GPU 绘制；缺少依赖时默认 GPU 安全停用 |
| 物品栏 | DS 实际 `DragonInventoryScreen`，三模式截图对照；无光影与 Complementary 下 GPU 模式的界面 `GUI_CPU_PASS > 0`、CPU 几何提交存在，同时背景世界 `GPU_PASS > 0`、正常纹理回读为零 |
| 多龙短时对照 | 同镜头 1/4/12 条同尺寸/同阶段的客户端玩家，三模式、两轮逆序、5 秒预热 + 15 秒采样；每帧 GPU 实体提交数量校验，清理归零；不是正式多人网络/性能矩阵 |

计算输出 float 容差 1e-5，法线/切线量化字节允许相差 1；颜色、光照、overlay、实体编号精确比较。测试比较实际 GPU 输出，正常游戏不执行测试回读。

## 缓存行为

实机测试在预热后连续执行 100 次同内容 `SkinData.deserializeNBT(..., body)` 与重编译标记设置，同时修改装备耐久并转动镜头。`equivalent-state.json` 中 `STATE_SYNC = 100`，`GENERATED = 0`。修改皮肤眼睛层 hue/发光设置后 `GENERATED = 1`；修改皮革盔甲染色后 `GENERATED = 1`；恢复之前的皮肤时 `GENERATED = 0`。这些记录分别保存在 `validation/runtime-iris/`。

优化模式的正常合成出现 `COPY`，不出现 `READBACK`；测试有意切换 `VANILLA` 的阶段会恢复 DS 的 CPU 回读，因此整个运行总计中的回读不是零。画面截图读取主 framebuffer，也不应与受管纹理的回读计数混淆。

GPU 运行记录中存在本体/盔甲的 `entity` 与 DS 发光的 `glow/entity` 命令；支持阴影的包另有 `shadow` / `glow/shadow`。重载与模式切换后恢复绘制；测试结束显式清理后 `afterClearTextureBytes = 0`、`afterClearMeshBytes = 0`。这些字节是受管资源估算，不是驱动总显存读数。

## 画面检查与边界

保存八包 `shader-N-textures.png` / `shader-N-gpu.png`，以及各包名称和 GPU 统计。检查了 BSL、Bliss、Complementary 和 Photon 的代表性画面：测试龙本体、盔甲、翅膀均可见，未观察到明显缺面或整体错色。截图拍摄时间不同，呼吸/翅膀姿态可能变化，不能作为逐像素一致性证明。

统计 JSON 随仓库提交；配对 PNG 保留在本地工程 `validation/`，不写入 Git 历史。GitHub Release 附件仅为最终客户端 jar。

物品栏预览暂时修改矩阵和光照，0.2.0-alpha.2 将 GPU 接管限制在 `GameRenderer.renderLevel` 世界阶段。界面保留原 CPU 几何提交与纹理优化，`GUI_CPU_PASS` 记录此行为。实际 DS 龙物品栏在无光影及 Complementary 下已检查，模型与原路径画面一致；该检查不覆盖完整化龙 Core 的所有额外界面。

多龙测试发现 Iris 相等的 `OuterWrappedRenderType` 会合并进一个刷新批次，原先按对象身份拆分的命令会遗留，导致漏绘与后端停用。现按渲染类型的相等语义合并姿态列表，刷新源仍按身份隔离。修复后测试要求每帧实体 GPU 提交至少为龙数的 1.8 倍（本体 + 盔甲），并检查没有故障；单纯进入 GPU 模式或只渲染第一条龙不能通过该门槛。

实例仅安装 DS、GeckoLib、本 mod、测试驱动，以及 Iris/Sodium（光影实例）。未装入完整化龙 Core、全部整合包模组和脚本。因此没有宣称龙娘、附属龙种全部动作、Curios/纹饰、第一人称、飞行/桶滚、技能粒子、所有材质/透明对象已通过验收。隔离实例会出现 DS 原动画表达式 `easeoutsine` 的解析日志；本检查未覆盖其完整动画修复链。

基础功能检查为 120 FPS 上限、1280×720、视距 6；多龙短时对照关闭限帧与 VSync。**没有完成 1/4/12 玩家 × 三模式 × 三重复、每组 60 秒预热 + 300 秒采样的正式性能矩阵，也没有据此宣称正式发布门槛已通过。** [基准流程](BENCHMARK.md)和自动汇总工具已提供。

## 正式发布尚缺

- 完整化龙整合包中的上述视觉与动作矩阵，特别是龙娘、附属形态、第一人称、额外界面预览和特殊顶点消费者。
- 八包的复杂发光/阴影/材质/透明回退场景，包括光影切换和反复资源重载。
- 正式多人性能矩阵与顶点提交/整体 P95 门槛。
- 多轮断线、退出世界、低资源预算与故障注入的持续资源稳定性验证。

本次默认 GPU；版本仍为 alpha，短时测量、功能检查与正式整合包验收分别记录。
