# 兼容清单

首版目标与检查方式：

| 组件 | 版本 | 检查 / 行为 |
| --- | --- | --- |
| Minecraft | 1.21.1 | 元数据限定 |
| Java | 21 | 编译与运行测试使用 JDK 21 |
| NeoForge | 21.1.248 | 已实际启动；更高版本未验收 |
| Dragon Survival | 2.0.71 | 启动时核验版本、注入方法描述符、私有集合字段和 framebuffer 调用归属 |
| GeckoLib | 4.9.3 | 核验版本和 `GeoRenderer` 提交接口；继续使用原动画与骨骼矩阵 |
| Iris | 1.8.14-beta.1+mc1.21.1 | 核验版本、刷新批次接口、实际 BufferBuilder 顶点字段/偏移 |
| Sodium | 0.8.13+mc1.21.1 | Iris 路径同时核验；与 Iris 一同实际启动 |
| OpenGL | 4.3（GPU 模式） | 驱动能力与计算 shader 编译检查；不满足时使用原 CPU 渲染 |
| DSBR | 0.2.0-alpha.1，mod ID `dsbr` | 本 jar 内统一协调后端，不因自身身份关闭 GPU；DS 2.0.71 停用旧 Bedrock/YSM 接管 |
| 独立优化原型 | `beloong_render_optimizer` | 检测到时停用集成补丁；升级时移除独立原型，保留一个 DSBR jar |

纹理补丁与 GPU 补丁独立开关。Iris 版本不匹配只关闭 GPU；DS/GeckoLib 版本或纹理接口不匹配关闭全部补丁。旧 Bedrock/YSM 仅对 DS 2.0.67 且 `initArmorMasks` / `getOrCreateDragon` 仍存在时开放，默认关闭，其完整运行回归未执行。metadata 中 DS 为可选依赖，以便在不匹配环境中载入并解释停用原因。实际启动的版本与日志保存在验证记录中。

## 渲染范围

| 路径 | 当前实现 |
| --- | --- |
| DS `DragonRenderer` 龙玩家、本体、盔甲 | 可缓存纹理、提交 GPU 几何 |
| DS 发光层 | 保留 DS `EYES + LIGHTNING_TRANSPARENCY` 加法混合；允许 GPU，保持原批次/阶段 |
| 龙娘、使用同一 DS 渲染器的附属形态 | 代码路径覆盖；完整整合包动作/画面回归待完成 |
| 末、地黄龙 | Core 的 `NpcRenderer` 路径，未被 GPU 接管 |
| 其他 GeckoLib / 其他模组实体 | 未被 GPU 接管 |
| 需要排序的透明材质 | CPU 原路径（DS 加法发光例外） |
| 特殊消费者、特殊顶点格式、自定义 `renderCube` / `createVerticesOfQuad` | CPU 原路径并记录原因 |
| 第一人称隐藏、骨骼定位、手持物、逐骨骼层 | 保留原遍历；各场景完整视觉验收待完成 |
| 编辑器像素读写和导出 | 保留 `DynamicTexture` 行为，显式访问才回读 |

GPU 初始化失败会在取消任何 CPU 几何之前关闭后端。计算分发异常会尝试用捕获快照回放 CPU 顶点，并停用 GPU。未知刷新源不接管；未完成刷新会记录故障并停止后端。发生纹理异常时恢复 DS 合成，当前已提交资源延迟到帧结束清理。

## 光影包

测试使用整合包现有八个 ZIP。实际开启光影后检查 Iris 当前包名、兼容状态、实体/阴影 GPU 命令及 GL 错误，保存 `TEXTURES` / `GPU` 配对截图。测试的是单龙、小场景功能路径，完整材质与透明场景未验收。

| 光影包 | 功能测试 |
| --- | --- |
| BSL_v10.1.1 | 启动、切换、实体/阴影绘制 |
| Bliss_v2.1.2_(Chocapic13_Shaders_edit) | 启动、切换、实体/阴影绘制 |
| ComplementaryReimagined_r5.9 | 启动、切换、实体/阴影绘制 |
| ComplementaryUnbound_r5.9 | 启动、切换、实体/阴影绘制 |
| MakeUp-UltraFast-9.5e | 启动、切换、实体绘制 |
| Sildur's Vibrant Shaders v2.01 Extreme | 启动、切换、实体/阴影绘制 |
| Solas Shader V3.7b | 启动、切换、实体/阴影绘制 |
| photon_v1.3b | 启动、切换、实体/阴影绘制 |

是否存在阴影阶段以包自身功能及验证 JSON 为准；不为没有提交该阶段的包宣称阴影验证通过。
