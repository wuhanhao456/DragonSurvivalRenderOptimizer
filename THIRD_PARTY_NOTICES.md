# Third Party Notices

## TouhouLittleMaid / simplebedrockmodel

本项目中的以下代码来自或参考自 TouhouLittleMaid 项目中的 `simplebedrockmodel` 实现，并已按当前工程需要做了精简与包名调整：

- `src/main/java/top/wu949/dsbr/client/bedrock/...`

原项目：

- TouhouLittleMaid
- 作者：TartaricAcid 及贡献者

许可：

- MIT License

MIT License 全文如下：

```text
MIT License

Copyright (c) 2020 TartaricAcid

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```


## 0.2.x optimization additions

Original optimizer source from BeLoong Render Optimizer (MIT; copyright (c) 2026 BeLoong contributors) is incorporated in `top.wu949.dsbr.optimizer`. The existing TouhouLittleMaid-derived Bedrock implementation and its notice above are preserved.

[OpenYSM](https://github.com/OpenYSM/OpenYSM/tree/a515d44686af77155a311b2a592327ca5d45a658) (MIT; copyright (c) 2026 OpenYSM) was a design reference for cached geometry, compute transformations and entity shader dispatch. No YSM animation engine, GeckoLib3, JNI or native code is copied or bundled. New paths use DS/GeckoLib baked models and original animations.

[Iris](https://github.com/IrisShaders/Iris) is an external optional dependency; its pinned entity vertex format and batching contracts were inspected for compatibility. No Iris classes are packaged.

## External runtime dependencies

- Minecraft 1.21.1: Mojang/Microsoft proprietary software, supplied by the user's installation.
- NeoForge 21.1.248: LGPL-2.1-only, external loader/API.
- Dragon Survival 2.0.71: Dragon Survival License 1.0, January 2024; all rights reserved to Viktoria Ershova (BlackAures1). Supplied separately by the user; not bundled or relicensed.
- GeckoLib 4.9.3: MIT, external model/animation library.
- Sodium 0.8.13: PolyForm Shield 1.0.0, optional external renderer.
- LWJGL: BSD-3-Clause, Minecraft-supplied Java/OpenGL binding. This project supplies no additional native libraries.
- Gradle wrapper: Apache-2.0; build tooling only. JUnit 5: EPL-2.0; test tooling only. Mixin/MixinExtras are supplied by NeoForge and are not shaded into this mod.

The optimizer's MIT license covers its original source only and does not change licenses or distribution permissions of any dependency or reference project.
