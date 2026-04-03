# Dragon Survival Bedrock Renderer

`Dragon Survival Bedrock Renderer` (`dsbr`) 是一个面向 `Minecraft 1.21.1 NeoForge` 的客户端附加模组，用于为 `Dragon Survival` 提供可切换的 Bedrock 渲染路径。

## 功能

- 支持在 `Dragon Survival` 原生 GeckoLib 渲染与本模组 Bedrock 渲染之间切换
- 支持特殊动画走 Bedrock 动画引擎或原版回退路径
- 提供第一人称头部显示、第一人称模型偏移、动画速度等游戏内配置
- 兼容常见第一人称和自由视角使用场景

## 环境

- Minecraft `1.21.1`
- NeoForge `21.1.213`
- Java `21`
- 需要安装 `Dragon Survival`

## 构建

```powershell
./gradlew build
```

构建产物会输出到 `build/libs/`。

## 说明文档

- 详细实现记录见 [实现说明.md](./实现说明.md)
- 第三方来源说明见 [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md)

## 许可证

本项目使用 [MIT License](./LICENSE)。
