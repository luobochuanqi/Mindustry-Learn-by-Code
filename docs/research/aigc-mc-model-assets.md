# AIGC 生成体素 / Minecraft 模型资产:方案调研报告(面向 MTurrets)

- 调研日期:2026-08-28
- 目标仓库:MTurrets(Kotlin + NeoForge,Minecraft 1.21.1,移植 Mindustry 方块/炮台玩法)
- 调研范围:文生3D/图生3D 商业工具、开源生成模型、体素专用工具、Blockbench 生态 AI、AIGC 贴图、开源自建管线、落地建议与许可风险
- 来源标注约定:
  - 【一手】= 官方文档 / 官方 GitHub 仓库 / arXiv 论文 / 官方博客,已实际抓取核实;
  - 【快照】= 官方域名页面,但本机无法直接抓取,内容经搜索引擎快照/摘要核实,引用时给出 URL;
  - 【二手】= 非官方来源(社区、评测、聚合站);
  - 【待核实】= 未能拿到可靠一手来源的论断。

---

## 0. 结论速览

| 需求 | 最务实答案 | 主要依据 |
| --- | --- | --- |
| 直接生成"方块感"模型 | Hyper3D Rodin(Voxel 风格 + Voxel ControlNet)或 Hunyuan3D 2.1(本地) | Rodin 官方 Voxel 风格页;Hunyuan3D-2.1 官方仓库 |
| 输出格式 | 所有 AI 工具只输出 glTF/GLB/OBJ/FBX,**没有任何一家直接输出 .bbmodel 或 Java 方块模型 JSON**,必须经 Blockbench 手工重建/转换 | 各工具官方文档 + Blockbench 官方格式文档 |
| Blockbench 生态 AI | 官方插件仓库**明确不收生成式 AI 插件**;可用第三方 Blockbench MCP 插件让 AI agent 直接驱动 Blockbench | Blockbench 插件仓库 README;blockbench-mcp-plugin 仓库 |
| 贴图 | SD/LoRA(如 Civitai 上的 MC 纹理 LoRA)、PixelLab AI、deep-pixels;或 Hunyuan3D-Paint 直接给已有网格上 PBR 贴图 | Civitai 模型卡(快照);Hunyuan3D-2 README 【一手】 |
| 许可 | 开源模型:Hunyuan3D 2.1(社区许可,有限商用)、TRELLIS.2(MIT)、TripoSR(MIT);SF3D(Stability 社区许可,<100 万美元年营收免费商用) | 各仓库 LICENSE 文件 【一手】 |

---

## 1. 背景:MTurrets 需要什么样的资产

MTurrets(1.21.1 / NeoForge)当前资产管线的现状(仓库内核实):

- **GeckoLib 4.9.2** 已接入(`gradle/libs.versions.toml`),炮台用 `GeoBlockRenderer` 渲染,资源为 `geo/duo.geo.json` + `textures/block/duo.png` + `animations/duo.animation.json`(`DuoRenderer.kt`)——即 **Bedrock 几何格式(.geo.json)+ PNG 贴图 + 动画 JSON**,用 Blockbench 的 "GeckoLib Model" 格式制作/导出。**【2026-08-28 注】本报告成文当日 GeckoLib 已被移除(ADR-0002),Duo 现为静态方块模型;报告中依赖 .geo.json 的导出路径请按 Java Block/Item JSON 理解。**
- Arc / Meltdown 炮台改用**静态方块模型(Mindustry 原版贴图)**(CHANGELOG.md)。
- ADR 0001 记录:"**Mindustry 开源资产保留并注明出处**"——Mindustry 仓库本体是 **GPL-3.0**【一手:github.com/Anuken/Mindustry,License: GNU GPL v3.0】。

因此评估任何 AI 方案的最终标准是:**产出能否落成 (a) .geo.json(GeckoLib 实体/炮台)或 (b) Java Edition 方块/物品模型 JSON(静态方块),外加 16 的幂次像素贴图**。

### 1.1 目标格式速览(为什么 AI 直接输出不了)

- **Java Edition 方块/物品模型 JSON**:`assets/<ns>/models/*.json`,元素**只能是长方体(cuboid)**,`from/to` 限 -16..32,每面 16x16 UV,旋转限 22.5° 增量(1.21.6 前)【一手:minecraft.wiki/w/Model】。
- **Bedrock 几何(.geo.json)/ GeckoLib**:骨骼 + 立方体/网格 + MoLang/动画;Blockbench 内置 "GeckoLib Model" 格式直接导出 `.json (bedrock geo)`【一手:blockbench.net/wiki/blockbench/formats】。
- **Blockbench 自身**:开源(GPL-3.0),支持导出 `.obj / .gltf / .fbx / .dae` 与 `.bbmodel`;官方声明"用 Blockbench 创建的资产(模型、贴图、动画)归你所有"【一手:github.com/JannisX11/blockbench README + LICENSE.MD】。
- **AI 生成模型输出**:均为 **GLB/OBJ/FBX 等通用网格格式**(见第 2、3 节各条目)。结论:AI→通用网格→Blockbench 手工调整(或第三方转换)→目标格式,是**唯一现实路径**;".bbmodel 直出"目前只有个别第三方小工具声称,未经可靠验证(见 4.6)。

---

## 2. 文生3D / 图生3D 商业工具

### 2.1 Hyper3D Rodin(Deemos)— 本报告最推荐的商业工具

- 提供方 / 链接:Deemos / [hyper3d.ai](https://hyper3d.ai/)· API 文档 [docs.hyper3d.ai](https://docs.hyper3d.ai/)(原 docs.deemos.dev)
- 许可证 / 状态:商用 SaaS(免费试用 + 订阅/积分制,API 定价见 [hyper3d.ai/pricing](https://hyper3d.ai/pricing?lang=en))
- 体素/MC 能力【一手:hyper3d.ai/styles/voxel 页面全文核实】:
  - 官方定位页面:"**AI Voxel 3D Model Generator** —— Prompt the blocky, cube-built look of MagicaVoxel and Minecraft";支持文本/参考图直接生成体素风格资产,官方提示词配方("voxel style, built from unit cubes, stair-stepped edges, 16-color palette")。
  - 官网首页列有 **Voxel ControlNet**(体素控制)与 Bounding Box / PointCloud ControlNet【一手:hyper3d.ai 首页】。
  - 导出:**GLB / OBJ / FBX**(官方 Voxel 页面);另有 OmniCraft 网格编辑器做 "voxel remeshing" 后处理(调整立方体尺寸)。
- 与 Blockbench/Java 格式兼容性:输出仍为通用网格,**不是 .bbmodel、不是 Java JSON、不是 .geo.json**;需要导入 Blockbench 手工重建为方块/骨骼(见第 7 节管线)。
- 生态:官方插件支持 Blender / Unity / Unreal / Godot / Maya / 3DS Max / ComfyUI【一手:hyper3d.ai 首页插件列表】。
- 评价:风格控制(Voxel 预设 + ControlNet)在同类中最好,适合 MTurrets"炮塔机器"类资产;缺点是无 Java 格式直出、SaaS 依赖与订阅成本。

### 2.2 Tripo(Tripo AI)

- 提供方 / 链接:Tripo AI / [tripo3d.ai](https://www.tripo3d.ai/)、API [platform.tripo3d.ai](https://platform.tripo3d.ai/docs)
- 许可证 / 状态:商用 SaaS;API 按次计费
- 输出格式【快照:官方页面 "Export to glb, fbx, obj, usd, stl, and schematic"(tripo3d.ai/content/en/compare/the-best-3D-model-building-design);meshy.ai 官方对比页亦列出 Tripo 支持 GLB/FBX/OBJ/STL/3MF】:
  - 注意:"schematic" 指 **Minecraft 世界存档结构(schematic)**,用于建筑场景,不是物品/实体模型;对模组资产用途不直接相关【快照,二手佐证:design4real.de】。
- 体素/MC 风格:未发现官方"Voxel/Minecraft 风格"预设的一手证据【待核实】;定位是高质量通用资产生成。
- 与开源生态的血缘:TripoSR / TripoSG(见 3.4/3.5)由 Tripo 与 Stability、VAST 合作开源,API 是同一模型的商业化入口。
- 评价:质量高、速度快,但风格上不如 Rodin 有明确体素控制;对 MTurrets 属"可用但需更多手工"。

### 2.3 Meshy

- 提供方 / 链接:Meshy / [meshy.ai](https://www.meshy.ai/)
- 许可证 / 状态:商用 SaaS
- MC/体素相关【快照:官方博客 "How to Create Minecraft 3D Prints"(meshy.ai/blog/minecraft-3d-print)与 "Minecraft Skin to 3D Model Converter"(meshy.ai/blog/minecraft-3d-model),声称支持把 Minecraft 结构像素图/皮肤转成体素风格模型】:
  - 官方对比页列出 Meshy 支持导出 **FBX、GLB、OBJ、STL、3MF、USDZ、BLEND、DXF**【快照:meshy.ai/compare/meshy-vs-tripo】。
- 评价:MC 场景宣传集中在"皮肤转摆件/3D 打印",对模组内游戏资产(方块/实体模型)支持是通用网格输出;无 Java 格式直出。

### 2.4 Luma Genie(Luma AI)

- 提供方 / 链接:Luma AI / [lumalabs.ai/genie](https://lumalabs.ai/genie)(官网与 Genie 文档页本机无法连接,以下为二手+论文佐证)
- 状态:商用(免费额度 + 订阅);Genie 1.0 是 Luma 的文本/图像→3D 模型【二手:Meta 3D AssetGen 论文(NeurIPS 2024)将其列为对比基线,aalto/a16z 等评测亦提及;a16z 投资公告 [a16z.com/announcement/investing-in-luma-ai](https://a16z.com/announcement/investing-in-luma-ai/)】
- 体素/MC 能力:**未找到任何一手/可靠来源说明 Genie 支持体素或 Minecraft 风格输出**【待核实】;定位偏写实/通用对象生成。
- 评价:对 MTurrets 不是首选;若只做炮台概念参考可用。

### 2.5 CSM(Common Sense Machines)

- 提供方 / 链接:CSM / [csm.ai](https://www.csm.ai/)(本机 DNS 无法解析,官网未能直接访问)
- 状态:商用 SaaS;官方 LinkedIn 公告有 **Chat-to-3D(2025-04)、Image-to-Kit(2025-05)** 等功能【快照:linkedin.com 官方账号帖子】;第三方测评称支持图像/草图/文本→3D、可导出到 Blender/ZBrush 等【二手:tasarim.ai、moge.ai、3daistudio.com】
- 体素/MC 能力:未发现一手证据【待核实】。
- 评价:与 Rodin/Tripo 同赛道,证据强度最弱,暂不推荐作为首选。

### 2.6 补充:Scenario(游戏资产专用平台)

- [scenario.com](https://www.scenario.com/) 面向游戏资产,其"3D 生成"文档提到可用 Rodin(Gen-1/Gen-2)并支持 Minecraft/The Sandbox/MagicaVoxel 风格关键词【快照:help.scenario.com 官方知识库】。作为游戏资产聚合出口值得留意,但同样不直出 Java 格式。

---

## 3. 开源模型(可本地自建)

### 3.1 Hunyuan3D 系列(腾讯)— 完整开源且带 PBR 贴图,首选

- 仓库:2.0 [github.com/Tencent/Hunyuan3D-2](https://github.com/Tencent/Hunyuan3D-2)、完整开源版 2.1 [github.com/Tencent-Hunyuan/Hunyuan3D-2.1](https://github.com/Tencent-Hunyuan/Hunyuan3D-2.1)、技术报告 2.5 [arXiv:2506.16504](https://arxiv.org/abs/2506.16504)
- 【一手】2.1(2025-06-13 发布):"第一个生产级的 3D 资产生成模型",**完全开源(权重 + 全部训练代码)**,两阶段:**Hunyuan3D-Shape-v2-1(3.3B,图→网格)+ Hunyuan3D-Paint-v2-1(2B,PBR 贴图生成,支持金属度/粗糙度)**;显存需求:仅形状 10GB,形状+贴图 29GB;支持 Windows/macOS/Linux;自带 Gradio App、本地 API server 与 **Blender addon**;官方明确支持"对**手工网格**做贴图(texture generation for handcrafted mesh)"——这对 MTurrets 现成 Blockbench 模型上 AI 贴图是直接可用的功能。
- 【一手】2.0(2025-01-21)论文 [arXiv:2501.12202](https://arxiv.org/abs/2501.12202);2.5(2025-06-23)只发布了**系统技术报告**(LATTICE 形状模型最大 10B + PBR 贴图),**未检索到 2.5 权重/代码仓库**【待核实;社区有"2.5 未开源"的抱怨,二手:r/StableDiffusion 2025-09】。完整开源的**最新版是 2.1**。
- 输出:trimesh → **glb/obj** 等【一手 README】;无 .bbmodel/Java JSON 直出。
- 社区扩展【一手 Hunyuan3D-2 README "Community Resources"】:ComfyUI-3D-Pack、ComfyUI-Hunyuan3DWrapper、Windows 整合包(Hunyuan3D-2-WinPortable)。
- 许可:**Tencent Hunyuan 3D 2.1 Community License**【一手,LICENSE 全文核实】:
  - 免费、免版税,**但明确排除欧盟、英国、韩国**(仅限 "Territory" 内使用);
  - **"Tencent claims no rights in Outputs You generate"**(第 6.d 条)——生成的网格资产归属于使用者;
  - 月活 >100 万需向腾讯另行申请许可;不得用输出训练其他 AI 模型(AUP 第 5.b 条);
  - 分发 Works(代码/权重)需附带协议文本与 Notice。
  - 对 MTurrets 的意义:**生成输出用于开源/免费模组基本可行**,但欧洲/英国/韩国的使用者不能使用该模型(若开发者在上述地区,需注意);2.1 与 2.0 许可同族(2.0 仓库同样为腾讯社区许可,2.1 文本已核实)。

### 3.2 Microsoft TRELLIS / TRELLIS.2 — MIT,最快、最新

- TRELLIS(2024-12)[github.com/microsoft/TRELLIS](https://github.com/microsoft/TRELLIS),CVPR'25 Spotlight,SLAT 结构化潜空间,图/文→网格/高斯/辐射场;仓库 **MIT**;需 ≥16GB 显存(Linux 优先);权重托管于 Hugging Face【一手 README;NVIDIA NIM 模型卡亦标 MIT,二手佐证:build.nvidia.com】。
- **TRELLIS.2(2025 年末)**[github.com/microsoft/trellis.2](https://github.com/microsoft/trellis.2),论文 [arXiv:2512.14692](https://arxiv.org/abs/2512.14692):**4B 参数,图→3D,512³ 分辨率约 3 秒(H100)**,O-Voxel 表示可处理开放曲面/非流形;输出含 **Base Color + Roughness + Metallic + Opacity 的完整 PBR**,导出 **GLB**;仓库 **MIT**,需 ≥24GB 显存,仅测试 Linux【一手 README】。
- 对 MTurrets:MIT 许可最干净;3 秒出 GLB 适合批量迭代;缺点显存门槛高、Linux 优先(Windows 需自行折腾)。

### 3.3 Stability AI Stable Fast 3D(SF3D)

- [github.com/Stability-AI/stable-fast-3d](https://github.com/Stability-AI/stable-fast-3d),论文 [arXiv:2408.00653](https://arxiv.org/abs/2408.00653)
- 单图→**带 UV 展开与光照解耦**的网格,预测材质参数(可直接进游戏);约 **6GB 显存**,支持 CPU/MPS(实验性);ComfyUI 节点;输出 **GLB**;内置重网格选项(triangle/quad)【一手 README】。
- 许可:**Stability AI Community License**【一手,LICENSE.md 全文核实】:研究/非商用免费;**年营收 < 100 万美元可免费商用(需按条款使用);>100 万美元需 Stability 企业许可**;输出所有权归使用者(IV.c.iii)。
- 对 MTurrets:显存亲民,UV/光照解耦对"把生成贴图重画到 Blockbench cube"很友好;商用注意营收阈值。

### 3.4 TripoSR(Tripo + Stability)

- [github.com/VAST-AI-Research/TripoSR](https://github.com/VAST-AI-Research/TripoSR),论文 [arXiv:2403.02151](https://arxiv.org/abs/2403.02151)
- 单图→网格 **<0.5s(A100)**,约 6GB 显存;**MIT**(代码+权重+在线 demo 均 MIT)【一手 README】;输出 GLB(可烘焙贴图)。SF3D 即在其基础上改进。
- 2024 年的模型,质量已被 Hunyuan3D/TRELLIS.2 明显超越;适合当"低保真草图"步骤。

### 3.5 TripoSG(VAST / Tripo)

- [github.com/VAST-AI-Research/TripoSG](https://github.com/VAST-AI-Research/TripoSG),论文 [arXiv:2502.06608](https://arxiv.org/abs/2502.06608);仓库 **MIT**,1.5B 整流流模型,图→网格(可限面数,如 `--faces 5000`),≥8GB 显存,GLB 输出【一手 README】;权重许可**待核实**(第三方称权重亦 MIT【二手:builderai.tools】,建议以 HF 模型卡为准)。

### 3.6 开源模型产出"适合 MC 的低多边形/块状风格"吗?

- **结论:风格上"能",格式上"不能"。** 上述模型均可通过提示词/参考图(如输入 MC 风格像素图)产出低多边形或块状资产;但输出是**任意三角网格**,不是轴对齐立方体:
  - 做 MC 方块/物品模型(纯 cuboid 约束):必须重建或体素化(见 7.4);
  - 做 GeckoLib 实体模型:骨骼与方块元素必须按 Blockbench "GeckoLib Model" 格式的规则重建(该格式以骨骼 + 立方体元素为主,详见 Blockbench 官方格式表,见 1.1),AI 网格仅作形状参照。
- TRELLIS.2 的 O-Voxel 稀疏体素表示与 MC 体素语义上接近,但导出仍是三角网格 GLB;没有体素网格直出。

---

## 4. 体素 / 方块风格专用工具

### 4.1 VoxAI(文本/图像 → 体素)

- [voxelai.ai](https://www.voxelai.ai/) —— 声称"文本和图像转 3D 体素资产,面向 MagicaVoxel、VoxEdit、Unity、Blender、Hytale"【快照:官网摘要;页面本机 429 无法完整抓取,细节待核实】。商业 SaaS。

### 4.2 VOXIFY(Blender 免费修改器,网格 → MC 风格体素)

- 免费 Blender Geometry Nodes 修改器,把任意网格体素化为 Minecraft 风格立方体资产,保持颜色/贴图,非破坏式工作流;作者 Masoud Rezaei【二手:80.lv 报道 2026-05 + 作者演示视频;下载页 gfxplugin.com(免费)】。**GitHub 源未检索到**【待核实】。
- 价值:把第 3 节生成的 GLB/OBJ 一键体素化,再按 MC 比例导出,是"通用网格→方块感"的最低成本桥。

### 4.3 MagicaVoxel(体素编辑器,非 AI)

- [ephtracy.github.io](https://ephtracy.github.io/)【一手,官网】;免费(作者 ephtracy);`.vox` 格式事实标准;纯手工/创意体素建模,无 AI 生成。

### 4.4 Goxel(开源体素编辑器)

- [github.com/guillaumechereau/goxel](https://github.com/guillaumechereau/goxel),**GPL-3.0**【一手 README】;导出 obj/png/magica voxel/qubicle 等;跨平台。可作为"手工体素化"的免费后处理工具。

### 4.5 Minecraft 皮肤类 AI 生成器(人物皮肤,非炮台)

- [skingenerator.io](https://skingenerator.io/)【一手,官网全文核实】:文本→64x64 皮肤 PNG(自研生成模型,付费分级),FAQ 明确"与 Mojang/Microsoft 无关"。
- [deep-pixels.com](https://deep-pixels.com/)("AI MC Texture")【一手,官网核实】:文本→**16x16/32x32 MC 物品贴图/像素画 PNG**。
- 其他如 mcskincreator.com / media.io / goenhance.ai 等同类【快照/二手】。
- 对 MTurrets:皮肤类与炮台资产关联弱;deep-pixels 的 16x16 贴图思路与 MC 纹理工作流契合,可借鉴。

### 4.6 Spaleforce:OBJ → Minecraft Java JSON(AI 转换)

- [spaleforce.com/minecraft-3d-model-generator](https://spaleforce.com/minecraft-3d-model-generator)【一手,页面核实】:上传 `.obj` + 材质 → AI 转成 **Java Edition 资源包 JSON 模型**(面向 Spigot/Paper custom model data)。作用是把通用网格转成 Java 物品模型,但按方块面数/尺寸约束优化有限,产物仍建议进 Blockbench 检查。
- 注意:此前搜索引擎摘要称其"直接生成 .bbmodel",与页面实述不符,已按页面为准【一手更正快照】。

---

## 5. Blockbench 生态内的 AI 方案

### 5.1 官方插件仓库:明确不收生成式 AI 插件【一手】

Blockbench 官方插件仓库 [github.com/JannisX11/blockbench-plugins](https://github.com/JannisX11/blockbench-plugins) README 明文规定:

> "Plugins that utilize generative AI are not accepted into this repository but may be shared externally."

即:**官方插件市场没有、也不会收录生成式 AI 插件**;AI 插件只能以外部渠道(独立 GitHub、自托管 URL)存在。这也是为什么"Blockbench AI 贴图/模型插件"很难在官方渠道搜到。

### 5.2 Blockbench MCP 插件(开源,GPL-3.0)— AI agent 直接操作 Blockbench

- [github.com/jasonjgardner/blockbench-mcp-plugin](https://github.com/jasonjgardner/blockbench-mcp-plugin)【一手 README】:
  - Blockbench 内置 MCP server(默认 `localhost:3000/bb-mcp`),任何支持 MCP 的 AI 客户端(Claude Code/Desktop、VS Code、Cline、OpenCode、Ollama 等)可**自然语言指令直接创建/编辑模型、UV、贴图、动画**;
  - 由插件 URL 一键加载(File > Plugins > Load Plugin from URL);
  - 372 stars、GPL-3.0(2026-08)。
- 意义:这相当于"AI 辅助建模"落地到 Blockbench 的目前最靠谱形态——不是黑盒生成,而是让 agent 用 Blockbench 原生能力搭 cube/骨骼/贴图,产物天然是 .bbmodel→Java JSON / bedrock geo。
- 同类还有 mcpmarket 收录的 "Bbmcp" 等第三方 MCP 插件【二手:mcpmarket.com】,生态在快速涌现。

### 5.3 像素画/贴图方向的 AI 工具(与 Blockbench 配合)

- **PixelLab AI**[pixellab.ai](https://www.pixellab.ai/)【一手,官网核实】:面向游戏的 AI 像素画生成器——角色、精灵表、tileset、场景、UI、四向/八向旋转、基于参考图保持风格一致、inpainting;浏览器或 Aseprite 插件形态,云端 GPU,声称不存储生成图片。适合给 Blockbench 模型生成 MC 风格 16x/32x 贴图素材。
- deep-pixels(见 4.5)同样适合 16x16 贴图。

---

## 6. AIGC 贴图 / 纹理

### 6.1 给"已有网格"生成贴图(对 MTurrets 最对口)

- **Hunyuan3D-Paint v2-1**【一手,Hunyuan3D-2.1 README】:2B 参数 PBR 贴图模型,**可对用户已有的网格(含手工建模)生成 RGB 或 PBR(金属/粗糙)贴图**,与 MTurrets"现成 Blockbench 模型上做贴图"完全对路;官方还有提升分辨率的多视图贴图流程(Hunyuan3D-2 README)。
- **SF3D/TripoSR** 的 UV 烘焙是"重建贴图",不适用于已有网格【一手 README 语义】。

### 6.2 Stable Diffusion 生态的 MC 风格模型/LoRA

- Civitai 上存在 MC 纹理生成 LoRA 等,例如:
  - "Minecraft 1.21 Texture Generator - Blocks"(SD1.x LoRA,"生成 16x16 方块贴图,基于 1.21 纹理训练"):[civitai.com/models/607046](https://civitai.com/models/607046/minecraft-121-texture-generator-blocks)【快照:civitai 页面本机无法抓取,经搜索引擎摘要核实;模型卡细节待核实】;
  - "Minecraft Block Generator 1.19":[civitai.com/models/57507](https://civitai.com/models/57507/minecraft-block-generator-119)(同上,快照)。
- 用法:ComfyUI/A1111 本地出 16x16/32x32/256x256 方块贴图 → 16 的幂次裁剪 → Blockbench 贴图面板微调。【二手:civitai 模型卡摘要】
- 版权提示:LoRA 基于《Minecraft》原版纹理训练,生成结果高度近似原版素材风格——**直接进正式版模组发行有商标/版权风险(近似 Mojang 资产的风格化复制品)**,建议仅作内部草图,或以原版为基础做明显二次修改(见第 8 节风险表)。

### 6.3 像素画/体素贴图工具与 PBR 烘焙

- PixelLab AI、deep-pixels(见 5.3/4.5)。
- **ArmorPaint**[github.com/armory3d/armorpaint](https://github.com/armory3d/armorpaint)【一手 README】:开源 PBR 直接绘制软件(源码仓库,二进制收费),可在生成模型上手工修正贴图。
- **Materialize**[boundingboxsoftware.com/materialize](https://www.boundingboxsoftware.com/materialize/):单张图生成 PBR 贴图(Height/Normal/Roughness 等)的免费桌面工具【本机 406 无法抓取,链接待复核】。

---

## 7. 开源可自建管线

### 7.1 ComfyUI + ComfyUI-3D-Pack(一站式本地 3D 生成)

- [github.com/MrForExample/ComfyUI-3D-Pack](https://github.com/MrForExample/ComfyUI-3D-Pack),**MIT**【一手 README】:
  - 已内置工作流:**Hunyuan3D 2.1(形状+贴图两段)、Hunyuan3D 2.0/1、TripoSG、TRELLIS、StableFast3D、TripoSR、Unique3D、CharacterGen、MV-Adapter(文本/图像→多视图贴图)、Stable3DGen、PartCrafter**;
  - **PartCrafter(单图→带部件分割的网格,输出部件 ZIP)**:对"炮台=底座+炮管+转轴"这种分部件需求非常契合【一手 README】;
  - Windows 预编译包(Comfy3D-WinPortable)/ComfyUI-Manager 一键装;依赖 Visual Studio Build Tools(Windows)。
- 配套:**ComfyUI-Hunyuan3DWrapper**(kijai)【一手,Hunyuan3D-2 README 社区资源】。

### 7.2 官方 Blender 集成

- Hunyuan3D-2 自带 **Blender addon**(blender_addon.py)+ 本地 API server(http://localhost:8080/generate,接收图片返回 GLB)【一手,Hunyuan3D-2 README】;2.1 同样提供 API server(api_server.py)【一手,2.1 README】。
- 即:Blender 里点按钮生成→后处理(减面/体素化)→导出,全程本地。

### 7.3 硬件门槛汇总(一手数据)

| 方案 | 显存 | 平台 | 速度 |
| --- | --- | --- | --- |
| Hunyuan3D-2.1(形状+贴图) | ~29GB(仅形状 10GB) | Win/macOS/Linux | 分钟级(2.1);2.0 形状 6GB / 形状+贴图共 16GB |
| TRELLIS.2(4B) | ≥24GB | Linux(官方声明) | 512³ 约 3s(H100) |
| TRELLIS(1.2B) | ≥16GB | Linux(官方声明) | 官方未公布标准速度【待实测】 |
| SF3D | ~6GB(+CPU/MPS 实验) | Win(实验)/Mac(实验)/Linux | 秒级(官方未公布精确值) |
| TripoSR | ~6GB | Win/Linux | <0.5s(A100) |
| TripoSG | ≥8GB | 未限定 | 官方未公布标准速度【待实测】 |

### 7.4 后处理链:从 GLB 到 Minecraft 资产

1. 生成 GLB/OBJ(上表任一);
2. **Blender**:减面(Decimate)/重拓扑(Remesh)/四边面重拓扑(开源 QuadriFlow [github.com/hjwdzh/QuadriFlow](https://github.com/hjwdzh/QuadriFlow)、Instant Meshes [github.com/wjakob/instant-meshes](https://github.com/wjakob/instant-meshes),均为开源【工具链接,未逐项抓取核实】);
3. **体素化**(可选):VOXIFY(4.2)或 MagicaVoxel/Goxel 手工重画,得到方块几何;
4. **Blockbench** 以 GLB/OBJ 为形状参照,手摆 cube/骨骼(官方 Wiki 系统性记载的是 OBJ/glTF/FBX/DAE 等**导出**到其他软件及导入 Blender/Unity 的指引【一手:blockbench.net/wiki/guides/export-formats】;Blockbench 对 OBJ/glTF 的直接导入能力以所用版本实测为准【待核实】);
5. 导出目标格式:
   - 静态方块/物品 → `Java Block/Item` 格式(`.json` java blockmodel);
   - 炮台实体 → `GeckoLib Model` 格式(`.json` bedrock geo + 动画 + PNG)。
   - Blockbench 官方格式能力表确认这两种导出路径【一手:blockbench.net/wiki/blockbench/formats】。

---

## 8. 对 MTurrets 的落地建议(含证据链与风险)

### 8.1 推荐路线(按性价比排序)

**路线 A(推荐,最务实):"图生3D 出参考体 → Blockbench 手工 cube 化 → 现有 GeckoLib/JSON 管线"**

1. 准备输入图:用 MC/体素风格的像素概念图。来源可选:
   - 用 PixelLab AI / deep-pixels / SD+MC LoRA 生成 16x/32x 概念贴图(自绘或 AI,见 6.2 许可提示);
   - 或沿用仓库现有 Mindustry 原版贴图(注意 GPL-3.0 与原作者署名,见 8.3)。
2. 图生3D(二选一):
   - 商业省事:**Hyper3D Rodin** Voxel 风格 / Voxel ControlNet,导出 GLB(2.1 节);
   - 本地免费:**Hunyuan3D 2.1**(最快上手,29GB 显存)或 **TRELLIS.2**(MIT、3s,24GB 显存)(3.1/3.2 节)。
3. 后处理:Blender 里看轮廓 → 视需要 VOXIFY 体素化(4.2)。
4. Blockbench:以 GLB/OBJ 为标准参考,**手摆 cube/骨骼重建**(炮管旋转轴、底座枢轴按 Mindustry 逻辑定);AI 生成物只当"形状草稿+贴图素材",不当"可直接用资产"——因为 Java/GeckoLib 格式约束 cuboid/骨骼,重建成本远低于修拓扑。
5. 导出:炮台 → GeckoLib Model 格式 `.geo.json`+PNG+动画 JSON(直接替换现有 `duo.geo.json` 模式,渲染器不用改);静态机器 → Java Block/Item JSON(替换 Arc/Meltdown 静态模型)。
6. 贴图:用 Hunyuan3D-Paint 给手工网格补 PBR 贴图,或 PixelLab/deep-pixels 出 16x16 风格贴图后手工微调。

**路线 B(零 3D 生成,最稳):概念图(AI 像素画)→ Blockbench 纯手工建模**

- 不动 3D 生成模型,只把 AI 用在贴图和概念层;版权面最小(AI 只产出贴图,且可由作者手工重画规避);适合炮塔数量多、要求风格统一的批量产出。

**路线 C(最激进,暂不建议作为主路径):"直接 prompt 出体素成品"**

- Rodin 的 Voxel 风格能出"看起来像 MC 模型"的资产,但仍是三角网格 GLB,且炮台需要炮管旋转部件、动画关键帧——AI 不产出这些,后续手工量并不比路线 A 少;可作为"批量拍脑门草图"用。

### 8.2 证据链摘要(每条结论对应的一手来源)

- "AI 无法直出 Java/GeckoLib 格式,必须 Blockbench 中转" ← 各工具官方输出格式均仅 glTF/OBJ/FBX(2.1-2.5、3.1-3.5)+ Blockbench 官方格式能力表(1.1)。
- "Hunyuan3D 2.1 是当前完整开源首选,带 PBR 贴图、支持手工网格贴图" ← 官方仓库 README + arXiv:2506.15442(2.1)、arXiv:2501.12202(2.0)、arXiv:2506.16504(2.5 仅报告)。
- "Rodin 有官方 Voxel/Minecraft 风格与 Voxel ControlNet" ← hyper3d.ai/styles/voxel 与官网首页正文。
- "Blockbench 官方不收生成式 AI 插件" ← blockbench-plugins 仓库 README 原文。
- "MTurrets 现有管线是 GeckoLib .geo.json + 静态 Java JSON,Mindustry 素材保留并注明 GPL-3.0 出处" ← 仓库 CHANGELOG.md / DuoRenderer.kt / docs/adr/0001-renovation-decisions.md。
- "使用开源模型生成资产的权利归属" ← 各 LICENSE 原文(3.1 Tencent 社区许可 6.d;3.3 Stability 社区许可 IV.c.iii;3.2/3.4 MIT)。

### 8.3 许可 / 版权风险清单

| 资产/工具 | 许可状态 | 对 MTurrets 的风险与做法 |
| --- | --- | --- |
| TRELLIS / TRELLIS.2 输出 | 仓库 MIT【一手】;权重经 NVIDIA 模型卡等交叉印证为 MIT【二手】 | 最干净;保留 MIT 声明即可,可商用 |
| TripoSR 输出 | MIT【一手】 | 干净 |
| Hunyuan3D 2.1 输出 | Tencent 社区许可【一手】 | 输出归使用者;**不可在欧盟/英国/韩国使用该模型**;月活>100万需授权;模组本体可不带协议(输出不属于 Works) |
| SF3D 输出 | Stability 社区许可【一手】 | 年营收<100 万美元免费商用;>100 万需企业授权;分发模型/派生时需署名 "Powered by Stability AI" |
| TripoSG 权重 | 仓库 MIT;权重许可【待核实】 | 商用前核对 HF 模型卡 |
| Rodin / Tripo / Meshy / Luma / CSM(SaaS) | 各服务条款 | 生成结果商用/分发条款需逐份核对 ToS,常见"输出归用户"但**训练/再分发限制各异**【待逐家核实】;云端生成还涉及素材上传的隐私/数据条款 |
| Mindustry 原版贴图(现仓库在用) | Mindustry GPL-3.0【一手】 | 模组整体开源(NeoForge mod 可独立)时,再分发需遵守 GPL-3.0 与作者署名;若做 AI 变体(图生3D 参考),**派生作品版权在多数法域不明确**,建议输出后人工重绘/明显修改,或仅作为风格参考不直接嵌入 |
| MC 原版纹理风格 LoRA(6.2) | 模型本身各 Checkpoint 许可不同 | 生成结果近似 Mojang 资产,**发行模组存在商标/版权风险**;仅内部草图或明显改写后再用 |
| Blockbench 自身 | GPL-3.0,但"用其创建的资产归你"【一手】 | 无风险 |
| GeckoLib | MIT(CurseForge 页面徽标)【快照】 | 依赖已在使用,无新风险 |

> 通用提示:AI 生成资产的可著作权性、第三方训练素材权利、逐字相似度侵权等问题在多数法域仍在形成判例;模组若计划上 CurseForge/Modrinth 公开分发,建议对"AI 直接产物"做一次人工改写记录(能说明创作过程的审阅/编辑即可降低争议面)。

### 8.4 待核实清单(调研时未能拿到一手来源)

- Tripo / Meshy / Luma / CSM 的服务条款中关于生成资产商用/再分发的具体条文(官网脚本重,本机无法直接抓取)。
- TripoSG 权重在 Hugging Face 的精确许可文本。
- Hunyuan3D 2.5(及后续 3.x,二手聚合站有提及)是否有权重/代码开源——截至调研日仅见技术报告。
- VoxAI 的完整功能与许可细节(官网 429)。
- VOXIFY 的官方 GitHub 仓库(仅见 80.lv/下载站/视频)。
- Civitai 两个 MC LoRA 模型卡的完整许可文本(页面无法抓取)。
- 各 SaaS 输出格式的"当前"完整列表(以官网为准,快照日期 2026-08-28)。

---

## 附录:调研过程与可达性说明

- 一手页面成功核实:全部 GitHub 仓库(API 方式)、arXiv、minecraft.wiki/w/Model、blockbench.net/wiki(格式表/导出)、hyper3d.ai(首页/Voxel 风格页)、pixellab.ai、deep-pixels.com、skingenerator.io、spaleforce.com、ephtracy.github.io(MagicaVoxel)、CurseForge GeckoLib 页、多个 LICENSE 原始文本。
- 无法直接抓取、改用搜索引擎快照/摘要 + 标注的:tripo3d.ai、meshy.ai、lumalabs.ai、csm.ai(域名解析失败)、civitai.com、voxelai.ai(429)、boundingboxsoftware.com(406)、huggingface.co(部分超时,MCP fetch_url 亦无响应)。
- 本报告未运行任何构建/测试/格式化命令;仓库文件除本报告外未做任何改动。