# DSRO 0.2.0-alpha.4 兼容边界

- Minecraft 1.21.1、Java 21、NeoForge 21.1.248。
- 纹理与 GPU 钩子锁定 Dragon Survival 2.0.71、GeckoLib 4.9.3，并检查目标方法与纹理合成字节码。缺少依赖或签名不符时停用优化。
- 龙魂另行检查 DS `DragonSoulRenderer` 方法；普通玩家优化不依赖龙魂路径。
- GPU 要求 OpenGL 4.3。Iris 路径锁定 Iris 1.8.14-beta.1+mc1.21.1、Sodium 0.8.13+mc1.21.1，检查其批次钩子及 54 字节实体布局。
- 物品栏与界面预览保持原 CPU 几何。需排序的透明材质、特殊消费器和自定义立方体几何保持原提交路径。
- 龙魂批次仅合并相同缓冲源、材质、网格、顶点布局和 Iris 属性的连续命令，保持原提交顺序。各实例动画、光照、覆盖值、缩放和朝向独立。
- 模式切换、资源重载、退出世界与断线统一清理。严重 GPU 故障停用本次会话的 GPU，计算故障回放已保存姿态；修复后重启。
- 发现独立 `beloong_render_optimizer` 时关闭集成补丁，避免重复注入。
- mod ID 仍为 `dsbr`。旧 Bedrock/YSM 后端及 DS 2.0.67 兼容接管已移除；只有 `dsbr-optimizer-client.toml` 注册为可编辑配置。

本次测试只执行新版一次，不代表所有模组、所有动作或所有光影包的完整兼容证明。范围与结果见 `validation/alpha4`。
