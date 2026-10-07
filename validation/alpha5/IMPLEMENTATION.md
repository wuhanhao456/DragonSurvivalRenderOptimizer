# alpha.5 实现和采用范围

修改前，本地检出与 GitHub main/tag `0.2.0-alpha.4` 同为 `4901eac29f3e84129b26916724c414c754070f92`，工作区干净，实际整包 alpha.4 jar 与 GitHub 附件摘要一致。旧 UNC 0.1.0 原型没有被覆盖，用户原客户端没有被重启、断线或改配置。详见 `SOURCE_BASELINE.json`。

采用的改动：

1. `OptionalNpcAdapter` 仅用元数据、类名与资源摘要探测可选 Core 0.10.1。末/地黄龙共用原 Gecko 烘焙模型、动画求值和骨骼遍历；不打包 Core 代码/资源。缺少或不匹配时保留龙生路径。
2. `RendererAdmission` 统一玩家、龙魂和 NPC 接管条件，按渲染器 ClassValue 缓存默认几何接口检查。自定义几何、特殊消费者、需排序透明路径继续 CPU。
3. 已有队列仅合并同源、同 RenderType、同模型、同渲染器和 Iris 属性的相邻命令。每个实例的姿态、法线、缩放、光照、覆盖值独立；不跨阶段或重排。NPC 网格故障只隔离该渲染器，共享 GPU 故障仍统一回退。
4. 每批姿态复制到可复用 native staging buffer，再一次 `glBufferData` 上传。保留 orphan 工作缓冲；不引入 persistent mapping、JNI 或自定义动画引擎。
5. 标准 CPU 回退路径复用矩阵、法线和顶点临时存储。可重入调用独占栈槽，退出后最多保留 16 槽；池与 staging 计入预算，资源清理时释放。运算和消费器收到的 11 个字段与 Gecko 默认路径逐项验证。

未采用的改动：同帧动画复用在当前场景没有命中，原型移到 `experiments/frame-animations`，不在 jar、配置或 Mixins 中。动画更新频率和技能骨骼计算保持原样。FancyMenu/KubeJS 只做独立热点分析，未改变实际整包设置；见 `COMPANION_COSTS.md`。

GPU 默认开启，物品栏仍 CPU。新增 `beloongNpcGpu` 和 `reuseCpuVertexScratch` 配置，原六项键和值、命令和统计字段兼容。细粒度归因默认关闭，增加 PLAYER/NPC/SOUL 及姿态上传轻量计数。

测量 jar 与最终 jar 仅增加启动签名保护的差异，所有运行时渲染/动画与资源内容逐字节相同；`final-guard-verification.json` 保留逐项核对。最终客户端做了独立启动和世界检查，不能把构建相同当成已完成游戏验证。
