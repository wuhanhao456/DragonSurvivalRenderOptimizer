# DSRO 0.2.0-alpha.4

更名为 **Dragon Survival Render Optimizer（DSRO）**，仓库名为 `DragonSurvivalRenderOptimizer`，新版附件为 `dsro-0.2.0-alpha.4.jar`。继续使用兼容 mod ID `dsbr`、原配置文件名与命令。更名只修改显示名称和日志文字，渲染代码逐文件核对相同。

移除旧 Bedrock/YSM 后端、菜单、配置及 legacy 命令。NeoForge 配置界面现在只显示六项有效优化设置，并提供中英文说明；已有优化配置继续有效。

增加龙魂不可变纹理共享、动画存在性缓存和 GPU 多实例合批。各龙魂保持独立逐帧姿态与原动画速度，同批保留颜色、光照和 Iris 阶段。新增专门的龙魂计数；排序透明材质和不支持的路径继续 CPU 回退。

按用户要求仅验证新版一次，包含龙魂、配置和连续动画，未运行旧版对照或三轮正式性能矩阵。证据与实际完成状态见 `validation/alpha4`。发布仍为预发布测试版。

16 项 Java 测试和 OpenGL 多实例标准／Iris 顶点校验通过；17 个客户端场景已执行，合批与龙魂连续骨骼数据得到验证。测试相机及创造模式界面问题使普通玩家完整动画和 GUI 预览尚未确认，帧时间不用于性能结论，原始运行结果为未全部通过。驱动源码已修正，按要求不重跑；限制见 [单轮报告](validation/alpha4/SUMMARY.md)。

后续按要求对实际更名 jar 追加启动／世界检查，通过主菜单、进入世界、可见玩家及龙魂、玩家待机动画和物品栏 CPU 实体预览，未发现 DSRO 相关 ERROR/FATAL。整合包其他配方／资源报错单独列出。没有重复性能采样或原 17 个场景，完整玩家行走／飞行仍未确认。详见 [启动检查](validation/alpha4/smoke/SUMMARY.md)。

---

以下为历史版本记录，其旧兼容入口不再适用于 alpha.4。

# DSBR 0.2.0-alpha.2

本次默认启用 `GPU`（包含纹理优化），修复 GPU 模式物品栏预览异常及 Iris 多龙盔甲批次漏绘。全新配置无需执行 `/dsbr gpu`。已有配置中保存的模式继续生效，既有实例可将 `mode` 设置为 `"GPU"`。能力或兼容检查失败时仍使用原回退逻辑。

化龙客户端渲染优化已合入 DSBR，沿用 mod ID `dsbr`。针对相同皮肤状态反复失效导致的纹理重建，默认以外观内容缓存纹理，按可见需求生成，并直接复制合成结果到 GPU 纹理；编辑器或导出明确读取像素时才回读。

实验 GPU 后端沿用 DS/GeckoLib 的动画、骨骼矩阵和渲染层，通过计算着色器提交本体、盔甲和已验证的发光路径。接入 Iris 原有批次与实体/阴影 shader，支持能力检测和 CPU 回退。GPU 默认开启，`/dsbr gpu` 可重新选择 GPU；`/dsbr textures` 切换到仅纹理优化，`/dsbr vanilla` 恢复原流程。

物品栏等界面预览使用原 CPU 几何提交，继续保留纹理优化；GPU 只接管世界阶段，避免界面临时矩阵/光照被延迟绘制误用。Iris 按状态判等的渲染类型合并为同一批次，保留每条龙的独立姿态，避免相同盔甲只刷新一部分命令。

## 安装与升级

- Minecraft 1.21.1，Java 21，NeoForge 21.1.248 或更新的兼容版本。
- 优化路径锁定 Dragon Survival 2.0.71 / GeckoLib 4.9.3；光影路径锁定 Iris 1.8.14-beta.1+mc1.21.1 / Sodium 0.8.13+mc1.21.1。GPU 要求 OpenGL 4.3。
- 用客户端 jar `dsbr-0.2.0-alpha.2.jar` 替换旧 DSBR jar，同一实例只保留一份。移除独立原型 `beloong_render_optimizer` jar。
- 保留旧 `dsbr-client.toml`，新优化设置位于 `dsbr-optimizer-client.toml`。旧 Bedrock/YSM 引擎仅保留为 DS 2.0.67 的显式兼容路径，默认关闭；DS 2.0.71 使用本次优化和原动画。
- 不需要服务器安装，不修改存档，不包含第三方依赖 jar、本地库或测试驱动。

## 已验证与限制

最终客户端 jar 完成 10 项 Java 测试、4 项基准汇总工具测试和真实 OpenGL 纹理/计算顶点检查。无光影及 Iris/Complementary 的全新配置默认 GPU 启动通过；DS 实际龙物品栏三模式画面与统计对照通过，界面 CPU 预览、背景世界 GPU 绘制和零正常纹理回读同时成立。隔离 DS 实例中，100 次相同外观同步没有重建纹理，皮肤/盔甲染色变化各生成一次；旧配置迁移、资源重载、模式切换、缺少 DS/GeckoLib 的安全停用与统一清理通过。

本次重新执行八个现有光影包的切换、本体/盔甲/发光及可用阴影阶段检查，保存纹理/GPU 配对画面。优化阶段正常纹理回读为零，最终清理后受管纹理/网格资源估算均归零。最终 jar 的验证哈希和结果见 `validation/summary.json`、`validation/default-gpu-summary.json`。

同镜头、同模型/尺寸/装备的 1/4/12 条龙已做三模式短时渲染对照，GPU 在所测机器/场景中最快。每组预热 5 秒、采样 15 秒、两次且第二次逆序；使用本地合成客户端玩家，不测真实网络负载。具体 FPS、P50/P95/P99、CPU 提交与资源记录见 [性能报告](https://github.com/wuhanhao456/DragonSurvivalRenderOptimizer/blob/0.2.0-alpha.2/PERFORMANCE.md)。静止场景预热后原版也不重建纹理，因此仅纹理模式在这里存在内容键检查开销。

**这是测试版。** 尚未完成完整化龙整合包的龙娘/附属形态、全部动作/特殊渲染视觉回归，以及每组 60 秒预热 + 300 秒采样、三次重复的正式性能矩阵。详细兼容、验证证据和基准步骤随源码提供。

GitHub Release 附件仅包含最终客户端 `dsbr-0.2.0-alpha.2.jar`。源码、性能报告、兼容清单和 JSON 验证记录保留在仓库；第三方许可见 `LICENSE` 与 `THIRD_PARTY_NOTICES.md`。配对截图保留在本地工程的 `validation/`，不作为 Release 附件。
