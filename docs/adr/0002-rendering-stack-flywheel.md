# ADR-0002: 渲染与动画基座采用 Flywheel，移除 GeckoLib

状态：accepted（2026-08 敲定）

## 背景与决定

玩法路线确认包含传送带与战役规模（数百个持续动画部件：带面滚动、炮塔群、弹雨），逐 BlockEntityRenderer 每帧提交的渲染撑不住该量级，GPU 实例化从"可选优化"变成"必需基座"。决定：采用 **Flywheel**（Engine-Room 维护，MIT 许可，`dev.engine-room.flywheel:flywheel-neoforge-1.21.1`，maven.createmod.net 分发，按 Create 先例 jarJar 内嵌、玩家免安装）作为唯一的动画方块渲染基座；同时**移除 GeckoLib**，Duo 模型重做为 partial 子模型，动画一律代码驱动。本决定修订 ADR-0001 §3 的最小依赖集（GeckoLib → Flywheel）。

## 理由

- GeckoLib 的实际使用面核实为≈0：仅 Duo 一个渲染器使用；`duo.animation.json` 的 animations 为空对象，`registerControllers` 空实现——关键帧播放、Molang、控制器混合这些核心卖点全部未用，用到的只有"加载模型 + 代码设骨骼旋转"，而后者用 vanilla `PoseStack` 矩阵即可实现。
- 同平台同版本的 Create 验证了无动画库路线：可动部件为独立 partial 子模型（一份烘焙几何跨 BE 复用），层级旋转在代码矩阵栈中完成（`turret→up→barrels` 骨骼树与 Create 的 push/rotate/render 完全同构），动画角速度同步与跨包插值自带（`MechanicalBearingBlockEntity`）。
- `duo.geo.json` 为 11 骨骼 31 cube、零 mesh 元素——纯立方体，重导出为 Java block model 无形状损失；模型在 Blockbench 中重导出为 Java 格式即可，工作量为单模型分钟级。
- 两库并存意味着两套重叠的"几何+变换"管线，与 ADR-0001"裁剪至最小集"的原则冲突；Flywheel 同时覆盖传送带与弹雨的实例化需求，GeckoLib 不覆盖。

## 动画原则（约束后续内容开发）

所有动画为**状态 → 变换参数**的代码驱动：CPU 回退路径在渲染器内做矩阵变换（含跨包角度插值），GPU 路径写入实例字段（pos/rotation/speed/offset）由顶点着色器推进。**不引入动画资产格式**（关键帧 JSON、Molang）。若未来出现真正需要动画资产编辑器的内容（如复杂多段机械），再开新 ADR 推翻本决定。

## Considered Options

- **保留 GeckoLib、不引 Flywheel**：否决——战役规模下 BER 逐实例提交撑不住，且保有一个卖点未用的库。
- **自写实例化渲染**：否决——等于重造 Flywheel，Create 已把该库维护到生产级且许可干净。
- **GeckoLib 留作异形网格逃生口**：否决——逃生口当前闲置（资产全为轴对齐 cuboid + Mindustry 像素贴图）；网格方块模型在 1.21.6+ 已进 vanilla，真到那天走版本升级而非库回留。

## 后果

- 已实施：GeckoLib 依赖与 Duo 渲染实现（`DuoRenderer.kt`、`geo/`、`animations/`、`registerControllers`）删除；Duo 暂以静态方块模型渲染（无瞄准旋转动画），动画部件按 ADR-0002 原则以 Flywheel visual / partial 逐步重建。Flywheel 以 compileOnly(api)+runtimeOnly 引入，jarJar 内嵌在发布前评估。
- 渲染基座与玩法解耦决定（传送带物品为 BE 数据而非实体，见 `docs/research/create-engineering-patterns.md` §4.4）互为前提：数据化物品才能实例化渲染。
