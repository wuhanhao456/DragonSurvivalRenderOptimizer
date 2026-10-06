# DSBR 0.2.0-alpha.1

将化龙客户端渲染优化合入 DSBR，沿用 mod ID `dsbr`。针对相同皮肤状态反复失效导致的纹理重建，默认以外观内容缓存纹理，按可见需求生成，并直接复制合成结果到 GPU 纹理；编辑器或导出明确读取像素时才回读。

实验 GPU 后端沿用 DS/GeckoLib 的动画、骨骼矩阵和渲染层，通过计算着色器提交本体、盔甲和已验证的发光路径。接入 Iris 原有批次与实体/阴影 shader，支持能力检测和 CPU 回退。GPU 默认关闭，可用 `/dsbr gpu` 开启；`/dsbr textures` 返回默认纹理优化，`/dsbr vanilla` 恢复原流程。

## 安装与升级

- Minecraft 1.21.1，Java 21，NeoForge 21.1.248 或更新的兼容版本。
- 优化路径锁定 Dragon Survival 2.0.71 / GeckoLib 4.9.3；光影路径锁定 Iris 1.8.14-beta.1+mc1.21.1 / Sodium 0.8.13+mc1.21.1。GPU 要求 OpenGL 4.3。
- 用客户端 jar `dsbr-0.2.0-alpha.1.jar` 替换旧 DSBR jar，同一实例只保留一份。移除独立原型 `beloong_render_optimizer` jar。
- 保留旧 `dsbr-client.toml`，新优化设置位于 `dsbr-optimizer-client.toml`。旧 Bedrock/YSM 引擎仅保留为 DS 2.0.67 的显式兼容路径，默认关闭；DS 2.0.71 使用本次优化和原动画。
- 不需要服务器安装，不修改存档，不包含第三方依赖 jar、本地库或测试驱动。

## 已验证与限制

8 项 Java 测试、4 项基准汇总工具测试、真实 OpenGL 纹理/计算顶点检查通过。隔离 DS 实例中，100 次相同外观同步没有重建纹理，皮肤/盔甲染色变化各生成一次；旧配置迁移、资源重载、模式切换、清理以及缺少 DS/GeckoLib 的安全停用通过。

八个现有光影包完成短时切换和绘制检查，GPU 阶段正常合成纹理回读为零，最终清理后受管纹理/网格资源估算均归零。源码包独立目录重建与测试 jar 的 SHA-256 一致。

**这是测试版。** 尚未完成完整化龙整合包的龙娘/附属形态、全部动作/特殊渲染视觉回归，以及 1/4/12 玩家正式性能矩阵，不能据此宣称整体 FPS 提升或达到正式发布性能门槛。详细兼容、验证证据和基准步骤随源码提供。

发布包含客户端 jar、sources jar、完整工程/验证截图 ZIP 和 SHA-256 清单；源码及第三方许可见工程中的 `LICENSE` 与 `THIRD_PARTY_NOTICES.md`。
