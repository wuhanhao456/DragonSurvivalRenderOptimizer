# 最终客户端运行检查

最终 `dsro-0.2.0-alpha.5.jar` SHA-256 为 `db1f8a395b2f8228420c0c0ecf78747de5731702d695973aefe7278c35763c09`。以下均实际启动最终 jar；每个目录保存客户端/驱动摘要 `identity.json` 和模组文件摘要 `actual-mods.json`。测试驱动不进入发布客户端。

| 检查 | 证据 / 实际结果 |
| --- | --- |
| 只有 DS/Gecko | `runtime/...-022940`：只有 DS、Gecko、优化客户端、测试驱动四个 jar。默认 GPU、世界、物品栏三模式、配置和重载通过，清理为零 |
| 缺少 DS/Gecko | `runtime/...-023051`：标题和配置可用，优化安全停用，无 Core 等额外依赖 |
| Core 不匹配 | `runtime/...-023412`：隔离 Core 元数据改为 0.10.2，DS GPU 保持绘制，NPC GPU 为零，清理为零 |
| NPC 视觉/动作 | `runtime/...-023558`：28 项已完成断言/截图，含无光影与八包 CPU/GPU、六种真实服务端动作、独立比例/朝向/姿态、开关、重载、物品栏 CPU、模式。退出驱动错误使总体未完成，原结果未伪造通过 |
| NPC 退出复核 | `runtime/...-024730`：修正退出顺序，四项开关/重载/预览/模式及退出通过；退出先检查资源为零，再执行显式最终清理 |
| 龙魂合批 | `runtime/...-024924`：无光影/Reimagined 的 1/4/12 龙魂六景均有绘制、纹理共享和零采样回读；4/12 合批生效。该次总体动作断言失败，原失败 JSON 保留 |
| 动作复核 | `runtime-summary.json` 指向最终目录：以当前四个世界块的实际 `fakePlayerIndex` 验证三模型待机/行走/飞行九组、玩家及各龙魂的时间/姿态推进；吐息/恢复、预览、配置、模式/重载和清理另行检查 |

上表缩写对应 `runtime/dsbr-validation-plain-20261007-*` 或 `runtime/dsbr-validation-iris-20261007-*`。自动汇总由 `tools/assess_alpha5_runtime.py` 核对归档生成，没有改写原始失败的 pass 字段。

八包为 BSL v10.1.1、Bliss v2.1.2、Complementary Reimagined/Unbound r5.9、MakeUp 9.5e、Sildur v2.01 Extreme、Solas V3.7b、Photon v1.3b。人工逐组检查 CPU/GPU 截图，未见明显缺失、拉伸或姿态串用。动画会推进，不是逐像素等值测试；使用各包原配置，未穷尽所有材质/阴影设置。独立姿态、隐藏/扁平几何、法线/切线及标准/Iris 54 字节输出另见 `gl-results.json`。

只有龙生的检查中，100 次等内容同步及耐久/数量/修复费用变化不重建；真实皮肤/盔甲染色各更新一次，恢复旧皮肤命中缓存，常规和三种预览模式回读为零。模式切换/重载的混合统计窗口有显式 `getPixels` 懒回读，未逐调用归因；不能把跨模式汇总当作稳态 GPU 回读。全部配对稳态采样仍为生成/回读零。

重载无效纹理警告在 alpha.4 的相同四 NPC 场景复现，同步栈均为 Iris `HorizonRenderer → ExtendedShader → glBindTextureUnit`，处于天空阶段，GPU 未停用。具体纹理生命周期责任方尚未确定，本轮未修复，见 `reload-warning.json`。整包还存在原配方/资源报错和切换模型时 DS 的 MountingBone 提示，不声称整个日志无错误。

23 项 Java 检查、16 项 Python 工具检查、真实 OpenGL 和可选依赖字节码审计通过。当前范围不是完整兼容证明：Curios、手持物、桶滚、第一人称、技能粒子和所有透明材质组合仍未穷尽；独立服务端启动未另测。原失败/原生崩溃保留于 `INVALID_RUNS.md`。用户原客户端/世界/配置保持原样，无持续监测或重复长测；短测保护线与正式验收分别记录。
