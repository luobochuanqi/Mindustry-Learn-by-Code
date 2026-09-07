# ADR-0011: 钻头动件渲染——首个迁出"机器 baked model"的视觉

状态：proposed（2026-09）

## 背景

机械钻（`mechanical_drill`，2×2 蓝图锚点）需要渲染一个**自旋扇叶**（rotator）。ADR-0005 修订将其归类为"机器类维持逐格 baked model 不迁移"，但那套路线只覆盖静态外观——它没有为单个动件提供方案。要转扇叶，就必须给钻头开 Flywheel Visual。

## 决策

机械钻走 Duo/Scatter 同款"**单锚点 visual 渲整台、结构格渲染为空**"（ADR-0005 修订 #42 的炮台惯例）：

- 锚点 `DrillBlock` `getRenderShape = ENTITYBLOCK_ANIMATED`，几何全进 visual 的 `base` 实例。
- 3 个结构成员格改用渲染为空的成员块（`TurretStructuralBlock` 同款 `INVISIBLE`），不复用逐格 `cube_bottom_top`。
- `_full` 整机静态模型供物品图标；扇叶在 `_full` 里画成静态桨，转动只走 separate visual。
- 动件只转 `rotator`（扇叶）；`top` 与轴柱属静态 `base`。

## 动件动画

- **角度源**：客户端自持。`beginFrame` 里 `if (isRunning) angle += rotateSpeed * dt`。`isRunning` 已随 update tag 同步（`DrillBE.kt:117`），不为动画加网络字段。
- **转速**：恒定 `rotateSpeed = 2 rad/s` + `warmup` 渐入渐出，逐项对齐 Mindustry `Drill.java`：
  - `rotateSpeed = 2f`（`Drill.java:59`，机械钻未覆盖，用基类默认）
  - `warmupSpeed = 0.015f`（`Drill.java:40`），0→1 渐入，约 67 tick ≈ 1.1s 到满速
  - 档位（Reserve 数）只改产出速率，**不改视觉转速**。
- **相位持久化**：不持久；重载世界从 0 起转（纯装饰视觉）。
- **pivot**：rotator 绕固定轴 (16, 中心Y, 16)，`beginFrame` 里 `translate→rotateY→translate` 或 `rotateAround`。不用 `rotateYCentered`（四叶与 top 质心不同）。

## 与既有决策的关系

- **推翻 ADR-0005 修订的一段**："机器类（钻头…）维持逐格 baked model 不迁移"不再适用于**带动件的机器**。钻头是第一个迁出点；仍无动件的机器（电池/电力节点/窑炉/发电机）继续 baked，此条对它们仍成立。
  - 这是本票硬核 tradeoff：逐格 baked 无法表达动件，要么给钻头开 visual（本决策），要么接受"扇叶不转"的静态妥协（否决）。
- **沿用 ADR-0005 的"不建无 Flywheel 回退路径"**：Flywheel 是 jarJar 内嵌必装依赖，无回退。
- **复用 #42 炮台资产惯例**：`_full` 物品图、锚点 `ENTITYBLOCK_ANIMATED`、成员 `INVISIBLE`、`elementless particle 模型`（DataGen.kt:312-315 同款）。

## 后果

- 钻头从此与炮台同一渲染路径，资产三条入口（世界/物品/裂纹代理）同源。
- 结构成员块需要从 `StructuralBlock` 迁到渲染为空的成员块（`TurretStructuralBlock` 同款），或给钻头开专用块。
- 扇叶视觉转速不再随档位变化（对齐 Mindustry：档位只改产出）。