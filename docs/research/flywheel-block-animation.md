# Flywheel 方块动画：partial 模型、Visual 生命周期、实例数据与着色器调研（面向 MTurrets）

- 调研日期：2026-08-29
- 目标仓库：MTurrets（Kotlin + NeoForge，Minecraft 1.21.1，移植 Mindustry 方块/炮台玩法）
- 目标版本：Flywheel 1.0.6（`dev.engine-room.flywheel:flywheel-neoforge-api-1.21.1:1.0.6`+`flywheel-neoforge-1.21.1`，maven.createmod.net），依赖声明方式与 Create 6.0.11 同款（见 ADR-0002）
- 范围：partial 模型资产 → api/impl 模块拆分 → BE visual 在 NeoForge 1.21.1 的注册入口 → Visual 生命周期（构造/删除/`update` 脏语义/`tick` 节奏/`updateLight`）→ 实例数据契约（`InstanceType`/`LayoutBuilder`/writer/`TransformedInstance`/`setChanged`）→ 着色器侧（`flw_renderSeconds` 等 uniform 与用户 GLSL 入口）→ CPU 回退门控
- 来源标注约定：
  - 【一手】= 官方 GitHub 仓库源码，已实际抓取核实；Flywheel 引用为 `Flywheel@1.21.1/dev（commit cbbc490b8066a68e8f39a8f1af36c52eb617ba84，tarball 抓取自 codeload）:` 仓库相对路径:成员；Create 引用为 `ref/create:` 路径:行（Create 6.0.11，commit `0924e93`，分支 `mc1.21.1/dev`，浅克隆）；
  - 【快照】= 官方页面，本机无法直接抓取，经快照/摘要核实；
  - 【二手】= 非官方来源（社区、评测、聚合站）；
  - 【待核实】= 未能拿到可靠一手来源的论断。

> 注意：Flywheel 官方分支名是 `1.21.1/dev`（不是 `1.21.1`），源码模块布局为 `common/src/{api,lib,backend,main}/java`。本报告所有 Flywheel 引用均来自该分支。`26.1.2/dev` 是更新的 MC 版本线（非 1.21.1），勿混用。

---

## 0. 结论速览

| 问题 | 已核实答案 | 出处 |
| --- | --- | --- |
| api/impl 模块怎么拆，compileOnly 看到什么 | Create 用 `compileOnlyApi("dev.engine-room.flywheel:flywheel-neoforge-api-1.21.1")` + `jarJar(runtimeOnly("dev.engine-room.flywheel:flywheel-neoforge-1.21.1"))`。编译期只见 `api` 与 `lib` 两包；`backend`/`impl`（渲染后端、mixin、事件）打进完整 jar 运行时才有 | `ref/create:build.gradle:132-136` |
| BE Visual 在 NeoForge 1.21.1 的确切注册入口 | `dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer.builder(BlockEntityType).factory(Factory).skipVanillaRender(Predicate).apply()`；`apply()` 内部调用 api 层 `VisualizerRegistry.setVisualizer(type, visualizer)`。【一手】Create 的 `CreateBlockEntityBuilder.visual(...)` 最终在 `FMLClientSetupEvent` 里调的就是它 | `Flywheel@1.21.1/dev:common/src/lib/java/.../lib/visualization/SimpleBlockEntityVisualizer.java:apply()`；`ref/create:foundation/data/CreateBlockEntityBuilder.java:105-115`、`AllBlockEntityTypes.java:243` |
| partial 模型（非方块/物品的独立 JSON 模型）怎么加载 | `PartialModel.of(ResourceLocation)` 静态创建即完成"注册"；Flywheel 的 NeoForge 端自动挂 `ModelEvent.RegisterAdditional` + `ModelEvent.BakingCompleted` 把 `PartialModel.ALL` 里所有模型送入烘焙管线，mod 无需自己挂模型事件 | `Flywheel@1.21.1/dev:neoforge/src/main/java/.../FlywheelNeoForge.java:98-99`；`neoforge/src/lib/java/.../PartialModelEventHandler.java` |
| Visual 的生命周期绑定谁 | `BlockEntityVisual` 接口文档：visual 存活期 ≤ 其 BE 存活期；方块状态在 visual 位置发生变化（BE 未移除）时 visual 会被**删除重建**，因此可假定整个 visual 生命周期内方块状态恒定 | `Flywheel@1.21.1/dev:common/src/api/java/.../api/visual/BlockEntityVisual.java:24-30`；删除触发见 `impl/mixin/visualmanage/BlockEntityMixin.java`（注入 `BlockEntity.setRemoved`）与 `LevelRendererMixin.java`（state 变化 → queueRemove+queueAdd） |
| `update(pt)` 的脏语义：谁标记、多久一次 | `Visual.update(float)` 只在"区段更新队列"里被调用（`VisualManagerImpl.processQueue` → `storage.update` → `visual.update`），触发点是 LevelRenderer 的区段更新，**不是每帧、也不是 BE `setChanged()`**。文档明说它适合"实例很少变、动画走 GPU"的情形；帧级动画应实现 `SimpleDynamicVisual.beginFrame` | `Flywheel@1.21.1/dev:common/src/api/java/.../api/visual/Visual.java:update()`；`impl/visualization/VisualManagerImpl.java:queueUpdate/processQueue`；`impl/mixin/visualmanage/LevelRendererMixin.java:flywheel$checkUpdate` |
| 实例修改后谁负责上传（dirty 语义） | `Instance.setChanged()` → `InstanceHandle.setChanged()`（`AbstractInstance` 把三个方法标 final）。每次改完实例字段必须显式 `setChanged()`，否则后端不重传。`Instancer` 文档：实例修改自动可见且**持久**（不每帧重算），这正是"静态模型 + 每帧改角度"能跑的原因 | `Flywheel@1.21.1/dev:common/src/lib/java/.../lib/instance/AbstractInstance.java:setChanged()`；`common/src/api/java/.../api/instance/Instancer.java:createInstance()` 文档 |
| `tick` 的节奏 | `SimpleTickableVisual.tick(Context)` 每游戏 tick 末调用；`SimpleDynamicVisual.beginFrame(Context)` 每帧调用；二者**保证不同时执行**（同一 visual 内互斥）。`tick` 与 `beginFrame` 内获取 `Instancer`/创建 `Instance` 均安全 | `Flywheel@1.21.1/dev:common/src/lib/java/.../lib/visual/SimpleDynamicVisual.java`、`SimpleTickableVisual.java` |
| 光照更新 | `LightUpdatedVisual.updateLight(float partialTick)`：visual 构造后、且所在 section 收到光照更新时调用（多次 section 更新只回调一次）。`AbstractBlockEntityVisual` 提供 `relight(pos?, FlatLit...)` 一堆重载，内部 = `FlatLit.relight(packedLight, instances)` → `instance.light(packedLight).handle().setChanged()` | `Flywheel@1.21.1/dev:common/src/api/java/.../api/visual/LightUpdatedVisual.java`；`lib/visual/AbstractBlockEntityVisual.java:relight*`；`lib/instance/FlatLit.java` |
| `InstanceType`/`LayoutBuilder`/writer 契约 | `InstanceType<I>` = `create(handle)` + `layout()` + `writer()`（Java 对象 → 裸指针内存）+ `vertexShader()` + `cullShader()`；`LayoutBuilder.scalar/vector/matrix` 描述 native 布局，layout 字段名直接成为着色器里 `instance.<字段>`；`SimpleInstanceType.builder(...)` 是 lib 层现成组装器（Create 的自定义类型全用它） | `Flywheel@1.21.1/dev:common/src/api/java/.../api/instance/InstanceType.java`、`api/layout/LayoutBuilder.java`；`lib/instance/SimpleInstanceType.java` |
| 内置实例类型与 `TransformedInstance` | `InstanceTypes.TRANSFORMED`：layout = color(4B)+overlay(2s)+light(2u)+pose(mat4)，顶点着色器 `flywheel:instance/transformed.vert`。`TransformedInstance` 实现 `Affine` 流式 API（`translate/rotate/rotateXCentered/scale/setTransform(PoseStack.Pose)/setZeroTransform`），自带 `public final Matrix4f pose` | `Flywheel@1.21.1/dev:common/src/lib/java/.../lib/instance/InstanceTypes.java:TRANSFORMED`、`TransformedInstance.java`；Create 的 ROTATING 自定义类型 = `ref/create:foundation/render/AllInstanceTypes.java` |
| 渲染秒数等 uniform 怎么进用户 GLSL | 每帧 `FrameUniforms`（backend）把 `_FlwFrameUniforms` 统一块写入 UBO：含 `flw_ticks`、`flw_partialTick`、`flw_renderTicks`、`flw_renderSeconds`、`flw_systemSeconds` 等；用户着色器不需要 include 这些（prelude 自带），直接读名字即可 | `Flywheel@1.21.1/dev:common/src/backend/resources/assets/flywheel/flywheel/internal/uniforms/frame.glsl`（`float flw_renderSeconds;`）；`backend/engine/uniform/FrameUniforms.java:update()` |
| 用户顶点着色器怎么写 | 实现前置入口 `void flw_instanceVertex(in FlwInstance instance)`（由 backend 生成的 `_flw_main` 在 prelude 之后调用，`FlwInstance` 的字段即 InstanceType layout 的字段）；可 `#include "flywheel:util/quaternion.glsl"` 等内置工具（`flywheel:` 前缀由 Flywheel 的 glsl 导入器解析） | `Flywheel@1.21.1/dev:docs/flywheel/api/stage/vertex.glsl`、`backend/resources/.../internal/common.vert:_flw_main`；Create 例：`ref/create:src/main/resources/assets/create/flywheel/instance/rotating.vert` |
| CPU 回退门控是哪个库的 API | `dev.engine_room.flywheel.api.visualization.VisualizationManager` 的静态方法 `supportsVisualization(LevelAccessor)`。Create 渲染器第一行 `if (VisualizationManager.supportsVisualization(be.getLevel())) return;` 即 GPU 可用时跳过 vanilla BER | `Flywheel@1.21.1/dev:common/src/api/java/.../api/visualization/VisualizationManager.java:supportsVisualization`；`ref/create:content/contraptions/bearing/BearingRenderer.java:30` |

---

## 1. 依赖形态：api / impl 拆分与 compileOnly 视角

Create 6.0.11 的声明方式（MTurrets ADR-0002 照抄的就是它）：

```gradle
// ref/create:build.gradle:132-136
compileOnlyApi("dev.engine-room.flywheel:flywheel-neoforge-api-$minecraft_version:$flywheel_version")
jarJar(runtimeOnly("dev.engine-room.flywheel:flywheel-neoforge-$minecraft_version:$flywheel_version") {
    version { strictly "[${flywheel_version},2.0)"; prefer flywheel_version }
})
```

- **api 模块**（`flywheel-neoforge-api-1.21.1`，compileOnlyApi）：只含 `dev.engine_room.flywheel.api.*` 与 `dev.engine_room.flywheel.lib.*` 两个包 —— 即**全部接口 + lib 便捷层**，没有渲染后端、【一手】模块布局见 `Flywheel@1.21.1/dev`：`api/`、`lib/`、`backend/`（引擎/编译/glsl）、`impl/`（mixin、事件、VisualManager 实现）四层源码集。
- **impl 模块**（`flywheel-neoforge-1.21.1`，runtimeOnly+jarJar）：完整实现。**编译期**你在 IDE 里 import 的只能是 `api`/`lib` 两个前缀的类；`backend`/`impl` 类（如 `FrameUniforms`、`VisualManagerImpl`）你的代码永远 import 不到，也不该 import。
- Create 还在同文件里 `jarJar(runtimeOnly(...))` 内嵌 flywheel，玩家免装（ADR-0002 已决定 MTurrets 走同一先例）。
- 同一个 `$flywheel_version = 1.0.6`；Create 的 `ref/create:gradle.properties` 亦为 `flywheel_version = 1.0.6`。

**MTurrets 含义**：只依赖 `api`+`lib` 就能写全部动画代码（visual、实例、partial 模型）。若未来需要自定义 backend 钩子（如 FrameUniforms 级别的 uniform），那不在公开 API 面，须另议。

## 2. Partial 模型资产：一份烘焙几何跨 BE 复用

`PartialModel` 是"给不被任何方块/物品直接使用的 JSON 模型"的加载助手：

```java
// Flywheel@1.21.1/dev:common/src/lib/java/dev/engine_room/flywheel/lib/model/baked/PartialModel.java
public static PartialModel of(ResourceLocation modelLocation)  // 静态创建即注册（入 ALL 弱值表）
public BakedModel get()                                        // 烘焙完成后填充
```

- **注册无须 mod 参与**：`FlywheelNeoForge.registerLibEventListeners` 挂 `modEventBus.addListener(PartialModelEventHandler::onRegisterAdditional)` 和 `::onBakingCompleted`（`Flywheel@1.21.1/dev:neoforge/src/main/java/.../FlywheelNeoForge.java:98-99`）。`onRegisterAdditional` 把 `PartialModel.ALL` 的全部 `modelLocation` 注册为 standalone additional model，`onBakingCompleted` 把结果写回 `partial.bakedModel`。所以 MTurrets 只要在客户端类静态字段里 `PartialModel.of(...)` 就行，**不要**自己挂 `ModelEvent`。
- **资产路径约定**（Create 惯例，`ref/create:AllPartialModels.java:323-329`）：`private static PartialModel block(String path) { return PartialModel.of(Create.asResource("block/" + path)); }` → 模型 JSON 落在 `assets/<mod>/models/block/<path>.json`。纹理与普通模型同规则（`assets/<mod>/textures/block/...`）。
- **缓存**：`Models.partial(PartialModel)` 是已烘焙模型 → flywheel `Model` 的缓存入口（`RendererReloadCache`），同参数同对象，资源重载自动失效重建（`Flywheel@1.21.1/dev:common/src/lib/java/.../lib/model/Models.java`；`lib/util/RendererReloadCache.java`；FlywheelNeoForge 里 `EndClientResourceReloadEvent → RendererReloadCache.onReloadLevelRenderer()`）。
- **渲染素材**：`Materials.SOLID_BLOCK` / `CUTOUT_MIPPED_BLOCK` / `TRANSLUCENT_BLOCK` 等预置 `Material` 由 `BakedModelBuilder` 按 bake 时的 RenderType 自动选定（`Flywheel@1.21.1/dev:common/src/lib/java/.../lib/material/Materials.java`）——即 partial 模型用的材料随 JSON 模型声明的 `render_type`/cutout 决定，MTurrets 的方块贴图（现为 `assets/mturrets/textures/block/duo.png`）不需要额外材料代码。

## 3. BE Visual 注册：NeoForge 1.21.1 的确切入口链

Create 的调用链（全部【一手】核实）：

1. 注册期（`ref/create:AllBlockEntityTypes.java:242-246` 等）：`REGISTRATE.blockEntity("schematicannon", ...).visual(() -> SchematicannonVisual::new).renderer(() -> ...).register()` —— Registrate 的 `visual(factory[, renderNormally])`。
2. `CreateBlockEntityBuilder.visual(...)`（`ref/create:foundation/data/CreateBlockEntityBuilder.java:81-115`）：
   - 首次调用时用 `CatnipServices.PLATFORM.executeOnClientOnly(() -> this::registerVisualizer)` 做客户端守卫；
   - `registerVisualizer()` 把 `SimpleBlockEntityVisualizer.builder(getEntry()).factory(visualFactory.get()).skipVanillaRender(be -> !renderNormally.test(be)).apply()` 推迟到 `FMLClientSetupEvent` 执行（`OneTimeEventReceiver`）。
   - 文件头 import（`ref/create:foundation/data/CreateBlockEntityBuilder.java:22`）：`import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;` —— **Create 直接用的是 Flywheel lib 层的类**，不经任何 catnip 包装（catinp 只提供 `CatnipServices` 平台分流；小结见 §10 未能核实）。
3. Flywheel lib 侧（`Flywheel@1.21.1/dev:common/src/lib/java/.../lib/visualization/SimpleBlockEntityVisualizer.java`）：
   - `Factory<T>` 函数式接口：`BlockEntityVisual<? super T> create(VisualizationContext ctx, T blockEntity, float partialTick)`；
   - `Builder<T>`：`builder(BlockEntityType<T>)` → `.factory(Factory)` → `.skipVanillaRender(Predicate<T>)`（缺省 = 永远跳过 vanilla 渲染）→ `.apply()` 构造并调用：
   - `VisualizerRegistry.setVisualizer(type, visualizer)`（api 包，`common/src/api/java/.../api/visualization/VisualizerRegistry.java`），key 是 `BlockEntityType`。visual 工厂由此进入 `VisualizerRegistryImpl`，按 BE 类型分发。
4. 注册时机与线程：`apply()` 在客户端 mod 初始化阶段（FMLClientSetupEvent 内）调用即可；server 侧调用会因 `FlwApiLink` 是客户端实现而失败，Create 用 `executeOnClientOnly` 挡掉。

> catnip 版的 `net.createmod.catnip.render.SimpleBlockEntityVisualizer`（旧 Create/Catinp 文档常见）在 6.0.11 已不被 Create 本体的 BE 注册使用；`ref/create` 内 235+ 处 `dev.engine_room.flywheel` import 中，注册相关全部落在 `dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer`。

**MTurrets 落地（无 Registrate 的裸 NeoForge 写法，设计草图）**：

```kotlin
// 客户端 mod 入口（仅客户端执行；最简单是 FMLClientSetupEvent 里调一次）
val duoType: BlockEntityType<*> = ModBlockEntityTypes.DUO_BLOCK_ENTITY.get()
SimpleBlockEntityVisualizer.builder(duoType)
    .factory { ctx, be, pt -> DuoVisual(ctx, be as DuoTurretBlockEntity, pt) }
    .skipVanillaRender { true }   // GPU visual 在场时让 vanilla BER 闭嘴；回退见 §7
    .apply()
```

类名全部来自已核实源码：`SimpleBlockEntityVisualizer.builder(BlockEntityType)`（Flywheel）、`Factory`（Flywheel）、`ModBlockEntityTypes.DUO_BLOCK_ENTITY` / `DuoTurretBlockEntity`（`ref:src/main/java/xyz/luobo/mturrets/common/ModBlockEntityTypes.kt:38-40`、`common/turrets/DuoTurret.kt:23`）。

## 4. Visual 生命周期：构造、更新、删除与光照

### 4.1 构造与销毁触发

- **创建**：BE 所在区块 section 进入可视化管理器后，`VisualManagerImpl` 的队列事务 `ADD` → `BlockEntityStorage.createRaw` → `visualizer.createVisual(ctx, be, partialTick)`（`Flywheel@1.21.1/dev:common/src/main/java/.../impl/visualization/storage/BlockEntityStorage.java:createRaw`）。visual 工厂即 §3 注册的 `SimpleBlockEntityVisualizer`。
- **销毁**：`BlockEntity.setRemoved()` 被 mixin 注入 `flywheel$removeVisual` → `VisualizationManager.blockEntities().queueRemove(be)` → `storage.remove` → `visual.delete()`（`Flywheel@1.21.1/dev:common/src/main/java/.../impl/mixin/visualmanage/BlockEntityMixin.java`）。
- **方块状态变化**：`LevelRendererMixin.flywheel$checkUpdate` 比较新旧 state：不同 → `queueRemove` + `queueAdd`（即**删除重建** visual）；相同 → `queueUpdate`（`impl/mixin/visualmanage/LevelRendererMixin.java:flywheel$checkUpdate`）。因此 visual 构造期可放心缓存 `blockState`（`AbstractBlockEntityVisual` 构造器就缓存了 `this.blockState = blockEntity.getBlockState()`）。
- **接口契约原文**（`BlockEntityVisual` javadoc）：“BlockEntityVisuals exist for at most the lifetime of the block entity… If the block state at your BlockEntityVisual's position changes without removing the block entity, the visual will be deleted and recreated. Therefore … the block state is constant for the lifetime of the visual.”

### 4.2 基类给什么

```java
// Flywheel@1.21.1/dev:common/src/lib/java/.../lib/visual/AbstractBlockEntityVisual.java
protected final T blockEntity; protected final BlockPos pos;
protected final BlockPos visualPos;      // pos.subtract(ctx.renderOrigin()) —— 已经减过渲染原点
protected final BlockState blockState;
public BlockPos getVisualPosition()      // 给实例设置位置时用这个，不是 pos
public void setSectionCollector(SectionCollector)   // 默认报当前区块
public boolean doDistanceLimitThisFrame(DynamicVisual.Context)  // 距离远时按帧频降级
protected int computePackedLight()
protected void relight(BlockPos, FlatLit...) / relight(FlatLit...)  // 见 §4.4
```

`AbstractVisual`（`lib/visual/AbstractVisual.java`）提供 `visualizationContext`、`level`、`instancerProvider()`（= `visualizationContext.instancerProvider()`）、`renderOrigin()`、`delete()`（final 壳，防重复删，委托给子类必实现 `_delete()`）。

### 4.3 三个回调节奏（谁调、多久一次）

| 回调 | 接口 | 节奏与触发 | 适合 |
| --- | --- | --- | --- |
| `update(float partialTick)` | `Visual`（默认空实现） | 区段更新队列里调用（§0 表格第 4 行），非每帧 | GPU 驱动的动画（实例字段基本不动）、很少变的位姿 |
| `beginFrame(DynamicVisual.Context)` | `SimpleDynamicVisual` | **每帧**；`Context` 给 `camera()`、`frustum()`、`partialTick()`、`limiter()`；可与 `updateLight` 并行 | CPU 驱动的逐帧动画（炮塔瞄准插值） |
| `tick(TickableVisual.Context)` | `SimpleTickableVisual` | **每游戏 tick 末**；与 `beginFrame` 互斥 | 低频状态推进 |

（`Flywheel@1.21.1/dev:common/src/lib/java/.../lib/visual/SimpleDynamicVisual.java`、`SimpleTickableVisual.java`；api 接口 `common/src/api/java/.../api/visual/DynamicVisual.java`、`TickableVisual.java`。两个 `Simple*` 默认把 `planFrame`/`planTick` 包成 `RunnablePlan`，**实现者只需写 `beginFrame`/`tick` 方法体**。）

Create 的范例（`ref/create:content/contraptions/bearing/BearingVisual.java:40-88`）：

```java
public void beginFrame(DynamicVisual.Context ctx) {
    float interpolatedAngle = blockEntity.getInterpolatedAngle(ctx.partialTick() - 1);
    Quaternionf rot = rotationAxis.rotationDegrees(interpolatedAngle);
    rot.mul(blockOrientation);
    topInstance.rotation(rot).setChanged();      // 每次改完必须 setChanged
}
```

`OrientedRotatingVisual`（`ref/create:content/kinetics/base/OrientedRotatingVisual.java`）还示范了 `update(float pt)` 用法的典型形态：需要写角度时 `rotatingModel.setup(blockEntity).setChanged()` —— 但它内部是一个**自定义实例类型**（§6 ROTATING），角度推进在着色器里，所以 `update` 低频更新 speed/offset 就够，与 §4.3 表格一致。

### 4.4 光照：`updateLight` 与 `relight`

- 接口（`LightUpdatedVisual`）：`updateLight(float partialTick)` 在 **visual 构造后、所在 section 收到光照更新时**调用；多个 section 同时更新只回调一次。
- `AbstractBlockEntityVisual` 的 `relight(...)` 系列：`FlatLit.relight(packedLight, instances)` → 对每个实例 `instance.light(packedLight).handle().setChanged()`（`Flywheel@1.21.1/dev:common/src/lib/java/.../lib/instance/FlatLit.java`）。`FlatLit` 是"可被 relight"的标记接口（`light(int packedLight)` 链式），`ColoredLitOverlayInstance`（`TransformedInstance` 的父类）实现了它。
- Create 例：`BearingVisual.updateLight` 里 `relight(topInstance)`（`ref/create:content/contraptions/bearing/BearingVisual.java:61-64`）。
- 【一手】`updateLight` 与 `beginFrame` **可能并行**（`LightUpdatedVisual` javadoc），不要在两者里同时裸写同一堆字段而不加锁；Create 的模式是各写各的实例字段。

## 5. 实例数据契约：`InstanceType` / `LayoutBuilder` / writer / `Instancer`

### 5.1 获取与创建

```java
// VisualizationContext（api）:instancerProvider()  —— visual 构造时拿到
Instancer<I> instancer = ctx.instancerProvider().instancer(InstanceType<I> type, Model model, int bias);
I instance = instancer.createInstance();   // zeroed 实例，改字段 + setChanged() 后即生效
```

- **Instancer 不可跨帧保存**：`InstancerProvider` javadoc 原文 “It is NOT safe to store instancers between frames. Each time you need an instancer, you should call this method.”（同帧同参同对象，自带缓存）。**实例对象本身可以**：Create 在构造器里 `createInstance()` 一次、`beginFrame` 里改字段（BearingVisual 模式）。
- `Instancer.stealInstance(...)` 用于把实例从一个模型"换绑"到另一个（模型在实例生命周期中可替换）。
- 同 instancer 内的实例单 draw call；`bias` 控制不同 instancer 之间绘制顺序（小 bias 先画）。

### 5.2 `InstanceType` 契约（api 层，backend 实现）

```java
// Flywheel@1.21.1/dev:common/src/api/java/.../api/instance/InstanceType.java
I create(InstanceHandle handle);          // 工厂：new 一个 zeroed 实例
Layout layout();                          // native 内存布局（LayoutBuilder 描述）
InstanceWriter<I> writer();               // Java 字段 → 裸指针内存的序列化函数
ResourceLocation vertexShader();          // 顶点着色器（自动补 flywheel/ 前缀）
ResourceLocation cullShader();            // 包围球裁剪着色器
```

- `LayoutBuilder`（`api/layout/LayoutBuilder.java`）：`scalar(name, repr)` / `vector(name, repr, size)` / `matrix(name, repr, rows, cols)`，`build()`；`repr` 有 `FloatRepr.FLOAT/NORMALIZED_UNSIGNED_BYTE ...`、`IntegerRepr.SHORT` 等。**layout 字段名 = 着色器里 `instance.<字段名>`**。
- `SimpleInstanceType.builder(Factory).layout(...).writer(...).vertexShader(rl).cullShader(rl).build()` 是 lib 层组装器（`lib/instance/SimpleInstanceType.java`），Create 的自定义类型全部用它。
- 内置类型（`lib/instance/InstanceTypes.java`）：`TRANSFORMED`（color+overlay+light+pose mat4，顶点着色器 `flywheel:instance/transformed.vert`，cull `instance/cull/transformed.glsl`）、`POSED`、`ORIENTED`、`SHADOW`。`TRANSFORMED` 的 writer 是 `MemoryUtil.memPut*` 手写偏移（color 0 / overlay 4 / light 8 / pose 12，共 76B）。

### 5.3 `TransformedInstance` 内置变换（MTurrets 首选类型）

```java
// Flywheel@1.21.1/dev:common/src/lib/java/.../lib/instance/TransformedInstance.java
public final Matrix4f pose = new Matrix4f();   // 实例自己的裁剪相对矩阵
translate(x,y,z) / rotate(quat) / rotateX/rotateY/rotateZ(rad)
rotateCentered(rad, ax,ay,az) / rotateXCentered / rotateYCentered / rotateZCentered
rotateAround(quat, x,y,z) / scale(x,y,z) / mul(PoseStack.Pose) 
setTransform(PoseStack.Pose) / setTransform(PoseStack) / setIdentityTransform()
setZeroTransform()      // pose 全 0 → GPU 快速丢弃几何，等效"关掉这个实例"
// 继承自 AbstractInstance（final）：setChanged() / delete() / setVisible(boolean)
```

- pose 是 mat4，所以**层级旋转**（turret→up→barrels）既可以用 `mul(PoseStack)` 直接接 vanilla 矩阵栈（与 ADR-0002 的 push/rotate/render 同构），也可以手写 `pose.translate().rotateY().rotateX()` 链。`setZeroTransform()` 用于"未瞄准/休眠时不画"。
- `ColoredLitOverlayInstance` 是公共父类：`color(r,g,b,a)`、`light(int)`、`overlay(...)`（`FlatLit` → `ColoredLit` 链），`TransformedInstance`、`OrientedInstance` 共用。

## 6. 着色器侧：uniform 与用户 GLSL 入口

### 6.1 内置 include 与 uniform

- **include**：`#include "flywheel:util/quaternion.glsl"` 这类引用由 Flywheel 的 glsl 导入器（`backend/glsl/Import.java`）解析；内置工具与预置实例着色器在 `common/src/lib/resources/assets/flywheel/flywheel/{instance,util,light,cutout,fog,material}/`（【一手】资源列表）。
- **uniform（prelude 自带，无需 include）**：`uniforms/frame.glsl` 定义 `_FlwFrameUniforms` std140 块，含 `flw_viewProjection`、`flw_renderOrigin`、`flw_ticks`、`flw_partialTick`、`flw_renderTicks`、**`flw_renderSeconds`**、`flw_systemSeconds`、`flw_cameraPos` 等（`Flywheel@1.21.1/dev:common/src/backend/resources/assets/flywheel/flywheel/internal/uniforms/frame.glsl`）。由 `FrameUniforms.update(RenderContext)` 每帧写入（`backend/engine/uniform/FrameUniforms.java`）。`docs/flywheel/api/glsl-api.md` 明说“All uniforms are included in the prelude and do not have to be manually included”。
- **用户着色器入口**：`docs/flywheel/api/stage/vertex.glsl` 声明 `void flw_instanceVertex(FlwInstance i);` 与 `void flw_materialVertex();`。backend 生成的 `_flw_main`（`internal/common.vert:_flw_main`）依次调 `_flw_layoutVertex()` → `flw_instanceVertex(instance)` → `flw_materialVertex()`——**用户实现 `flw_instanceVertex` 即完成"把实例 layout 字段应用到顶点"**。`FlwInstance` 的字段集合 = 该 InstanceType 的 layout，结构由 backend 按实例类型生成。（具体生成规则的精确字段布局未见文档，记为 §10 待核实项，但不影响使用。）

### 6.2 Create ROTATING 的完整例子（我们 §6.1 的直接证据）

`AllInstanceTypes.ROTATING`（`ref/create:foundation/render/AllInstanceTypes.java`）：

```java
public static final InstanceType<RotatingInstance> ROTATING = SimpleInstanceType.builder(RotatingInstance::new)
    .cullShader(asResource("instance/cull/rotating.glsl"))
    .vertexShader(asResource("instance/rotating.vert"))
    .layout(LayoutBuilder.create()
        .vector("color", FloatRepr.NORMALIZED_UNSIGNED_BYTE, 4)
        .vector("light", IntegerRepr.SHORT, 2)
        .vector("overlay", IntegerRepr.SHORT, 2)
        .vector("rotation", FloatRepr.FLOAT, 4)     // 初始姿态四元数
        .vector("pos", FloatRepr.FLOAT, 3)
        .scalar("speed", FloatRepr.FLOAT)            // 度/秒
        .scalar("offset", FloatRepr.FLOAT)           // 初始角度偏移
        .vector("axis", FloatRepr.NORMALIZED_BYTE, 3) // 旋转轴（Byte 归一化）
        .build())
    .writer((ptr, instance) -> { /* memPut 手写偏移 */ })
    .build();
```

对应顶点着色器（`ref/create:src/main/resources/assets/create/flywheel/instance/rotating.vert`，全文仅 13 行）：

```glsl
#include "flywheel:util/quaternion.glsl"

void flw_instanceVertex(in FlwInstance instance) {
    float degrees = instance.offset + flw_renderSeconds * instance.speed;   // 角度在 GPU 上推进！

    vec4 kineticRot = quaternionDegrees(instance.axis, degrees);
    vec3 rotated = rotateByQuaternion(flw_vertexPos.xyz - .5, instance.rotation);

    flw_vertexPos.xyz = rotateByQuaternion(rotated, kineticRot) + instance.pos + .5;
    flw_vertexNormal = rotateByQuaternion(rotateByQuaternion(flw_vertexNormal, instance.rotation), kineticRot);
    flw_vertexColor *= instance.color;
    flw_vertexLight = max(vec2(instance.light) / 256., flw_vertexLight);
    flw_vertexOverlay = instance.overlay;
}
```

- 这就完全解释 §4.3 那张表：**只要把 speed/offset 设好，旋转是 `flw_renderSeconds` 驱动的，CPU 侧每帧什么都不用做**——`update` 低频更新即可（Create 的 `KineticBlockEntityVisual.update` 只重算 speed/offset/relight）。
- 值对：`RotatingInstance` 字段与 writer 的偏移一一对应；`setup(KineticBlockEntity)` 里 `rotationalSpeed = getSpeed() * 6`（SPEED_MULTIPLIER=6，`ref/create:content/kinetics/base/RotatingInstance.java`），坐标是 `visualPos`（已减 renderOrigin，`OrientedRotatingVisual` 构造器 `setPosition(getVisualPosition())`）。

## 7. CPU 回退门控：`VisualizationManager.supportsVisualization`

- API（【一手】`Flywheel@1.21.1/dev:common/src/api/java/.../api/visualization/VisualizationManager.java`）：`static boolean supportsVisualization(@Nullable LevelAccessor level)` —— **由 Flywheel 提供**（`FlwApiLink.INSTANCE.supportsVisualization(level)` 入 impl），不是 Create/catinp 的 API。
- 用法（Create 每一个带回退的 BER，`ref/create:content/contraptions/bearing/BearingRenderer.java:30`）：

```java
if (VisualizationManager.supportsVisualization(be.getLevel())) return;  // GPU 轨在场 → vanilla BER 直接退出
```

- 双轨模型：`AllBlockEntityTypes` 里几乎每个条目同时注册 `.visual(...)` **和** `.renderer(...)`（§3 例），`renderNormally=false` 的条目视觉在场时跳过 vanilla（`CreateBlockEntityBuilder` 把 `renderNormally` 反转成 `skipVanillaRender(be -> !renderNormally.test(be))`）。无 Flywheel/低配回退时 `supportsVisualization` 为 false，BER 照常跑 PoseStack 矩阵路径——与 ADR-0002 的"CPU 回退轨"设计一致。
- 同一层还有 `VisualizationManager.get(level)`（可空）供"要不要注册事件/查询可视状态"用（Create 的 `PortableStorageInterfaceMovement` 等用它判断是否驱动 visual，`ref/create:content/contraptions/psi/PortableStorageInterfaceMovement.java`）。

## 8. MTurrets 落地：DuoVisual 草图

> 设计草图语义：所有类型/方法名均已在上文一手来源中核实；`???` 处为本地仓库内尚未落地、将在实现期确定的占位。此段不是可编译代码。

```kotlin
// 设计草图 —— xyz.luobo.mturrets.client.visual.DuoVisual.kt
// 原则（ADR-0002）：状态 → 变换参数，代码驱动；不引入动画资产格式。
class DuoVisual(
    ctx: VisualizationContext,
    blockEntity: BaseTurretBlockEntity,
    partialTick: Float
) : AbstractBlockEntityVisual<BaseTurretBlockEntity>(ctx, blockEntity, partialTick),
    SimpleDynamicVisual {

    private val parts = listOf(
        TurretPart.DuoBase,  // models/block/turret/duo_base.json
        TurretPart.DuoHead,  // models/block/turret/duo_head.json
        TurretPart.DuoBarrelLeft,  // models/block/turret/duo_barrel_left.json
        TurretPart.DuoBarrelRight, // models/block/turret/duo_barrel_right.json
    )

    // 实例在构造期一口气建好，之后只改字段（Instancer 不跨帧存，Instance 可以）
    private val instances: List<TransformedInstance> = parts.map { part ->
        ctx.instancerProvider()
            .instancer(InstanceTypes.TRANSFORMED, Models.partial(part.model)) // Models.partial(PartialModel)
            .createInstance()
    }

    init {
        // 初始全部"关掉"或摆到静态位，避免构造后到首帧之间闪烁
        instances.forEach { it.setZeroTransform().setChanged() }
    }

    override fun beginFrame(context: DynamicVisual.Context) {
        val pt = context.partialTick()
        // BE 字段契约：currentRotation/targetRotation/currentPitch/targetPitch（float，已有网络同步）
        // 插值：yaw 走 wrapDegrees 最短角 + partialTick 外推（跨包插值范式见
        // docs/research/create-engineering-patterns.md §2.5 MechanicalBearingBlockEntity）
        val yaw = lerpYaw(blockEntity.currentRotation, blockEntity.targetRotation, pt)   // ???
        val pitch = lerpPitch(blockEntity.currentPitch, blockEntity.targetPitch, pt)     // ???

        // 层级矩阵：turret → up → barrels（与旧 GeckoLib 骨骼树同构，ADR-0002）
        // TransformedInstance 的 pose 是完整 Matrix4f：先置零再叠变换
        var idx = 0
        // --- base：静态位姿（yaw 烘焙进矩阵或留给 VB？这里选 CPU 烘焙，先求稳）
        val base = instances[idx++]
        base.setIdentityTransform()
            .translate(visualPos.x + .5f, visualPos.y, visualPos.z + .5f)
            .rotateY(Mth.DEG_TO_RAD * yaw)   // 座盘绕 Y
            .setChanged()
        // --- head：绕底座俯仰枢轴（y 抬升）旋转
        val head = instances[idx++]
        head.setIdentityTransform()
            .translate(visualPos.x + .5f, visualPos.y + PIVOT_Y, visualPos.z + .5f)
            .rotateX(-Mth.DEG_TO_RAD * pitch)
            .translate(0f, -PIVOT_Y, 0f)
            .setChanged()
        // --- barrels ×2：跟随 head（在其局部空间各偏 YAW_OFFSET ±）
        for (barrel in 2..3) { /* 同构：在 head 矩阵上再叠偏移 */ }
    }

    override fun updateLight(partialTick: Float) {
        relight(instances)   // FlatLit 批量（AbstractBlockEntityVisual.relight(Iterable)）
    }

    override fun _delete() {
        instances.forEach { it.delete() }
    }

    override fun collectCrumblingInstances(consumer: Consumer<Instance>) {
        instances.forEach(consumer::accept)   // 挖掘破坏动画
    }
}
```

- **为什么 TRANSFORMED 而不是自定义 ROTATING-like**：炮塔动画是"瞄准插值"，角度来自 BE 每 tick 同步的靶向数据，不是恒转速；GPU 驱动角度（§6.2 的 speed/offset 模式）适合恒速旋转件。TRANSFORMED + `beginFrame` 每帧烘焙矩阵是 Create 官方验证的最小形态（恰如 BearingVisual 用 ORIENTED 每帧改 `rotation`）。未来若做弹雨（大量同构弹体），再抄 `AllInstanceTypes.ROTATING` 的自定义 layout+着色器模式（§6.2 全套模板已在手）。
- **Create catnip 包装与裸 API 的差异（实现期注意）**：Create 的 `.visual(factory, false)` 把 `renderNormally=false` 反转为 `skipVanillaRender=true`（GPU 在场才跳）；MTurrets 直接用 lib builder 时传 `skipVanillaRender { true }` 即可，但**必须**同时保留一个 BER（§7 的 `supportsVisualization` 首行 return），否则回退路径是空的。渲染原点：基类已把 `pos - ctx.renderOrigin()` 算进 `visualPos`/`getVisualPosition()`，不要在 beginFrame 里再加 renderOrigin。
- **资产布局**：

```
src/main/resources/assets/mturrets/
├─ models/block/turret/duo_base.json         ← PartialModel.of("mturrets:block/turret/duo_base")
├─ models/block/turret/duo_head.json
├─ models/block/turret/duo_barrel_left.json
├─ models/block/turret/duo_barrel_right.json
└─ textures/block/duo.png                    ← 已存在（现方块贴图；part 模型 UV 从其上切）
```

每个 part JSON 按标准 block model 写（elements + UV + 相对原点轴对齐立方体），纹理沿用 `duo.png`；game 内模型文件路径 = `models/<partial path>.json`，所以 `PartialModel.of(rl("block/turret/duo_base"))` 对应 `models/block/turret/duo_base.json`（Create 惯例 `Create.asResource("block/" + path)` 同构，`ref/create:AllPartialModels.java:323-325`)。
- **BE 字段契约**（已同步，勿改通道）：`BaseTurretBlockEntity.kt` 已有 `currentRotation/targetRotation/currentPitch/targetPitch` 四个 float 走网络；DuoVisual 只读它们做插值，不再触碰网络。跨包插值所需的外推角速度/最短角差建议按 patterns 文档 §2.5 在 BE 侧补 `prevRotation/prevPitch` 快照或渲染侧自持衰减差（实现期定，文档 §2.5 已给三件套清单）。

## 9. 风险与坑（仅列有证据的）

1. **`update(pt)` 不是帧回调**：只在区段更新事务里触发（§4.3）。把瞄准插值写进 `update` 会导致"炮塔只在区块更新瞬间动一下"。帧级动画必须走 `SimpleDynamicVisual.beginFrame`（BearingVisual 即如此）。
2. **改完实例忘了 `setChanged()` = 画面不动**：`AbstractInstance.setChanged()` 是唯一上传信号；Create 代码里每处修改都跟 `.setChanged()`（BearingVisual:46/57）。`setZeroTransform()` 后也须 `setChanged()` 才会被后端采纳（"GPU 快速丢弃"靠的是**上传**零矩阵）。
3. **`Instancer` 不跨帧缓存**：javadoc 明令每帧 `instancerProvider().instancer(...)`；`Instance` 对象可以（也应该）构造期一次创建。
4. **Visual 生命周期 ≠ BE 生命周期**：方块状态变 → visual **删除重建**（§4.1），不要在 visual 里存"跨重建"缓存；`update` 里读到的 `blockState` 应按基类缓存值用。BE 是**虚拟 BE**（MTurrets 地图编辑类玩法常见）时尤其要核对：`BlockEntityStorage.willAccept` 对 `level.isEmptyBlock(pos)` 的虚拟 BE 会拒收（§4.1 storage 源码 `BlockEntityStorage.java:30-42`，「已核实」该检查存在；虚拟 BE 行为组合【待核实】）。
5. **资源重载**：`PartialModel` 在 `ModelEvent.BakingCompleted` 重填（§2），`Models` 缓存与程序在 `EndClientResourceReloadEvent` 重建（`FlywheelNeoForge` 注册），visual 随 `recreateAll` 全量重建（`VisualManagerImpl`/`BlockEntityStorage.recreateAll`）。F3+T 后若 visual 持旧 `BakedModel` 引用会悬空——不要自己缓存 `Model`（改用 `Models.partial` 的缓存）。
6. **版本漂移**：分支是 `1.21.1/dev`（非 `1.21.1`，后者 404【一手】）；`1.20.1/dev` 的 API 面不同（旧包名时代），网上 0.6.x 教程多数过期。锁定 `1.0.6`（Create 同款）并对照本报告文件路径复核。
7. **回退轨不能丢**：`skipVanillaRender` 只管 GPU 在场时跳 vanilla；`supportsVisualization=false`（未装/低配/关闭）时由 BER 兜底（§7）。只注册 visual 不注册 renderer，等于把回退轨挖了。
8. **`updateLight` 与 `beginFrame` 可能并行**（`LightUpdatedVisual` javadoc）：两者都写实例字段时保持各自独立写（Create 模式），不要共享可变中间状态。

## 10. 已核实清单

**Flywheel 源码（`1.21.1/dev`，commit cbbc490，以下均为【一手】抓取）：** `api/visualization/VisualizerRegistry`（`setVisualizer`）、`api/visualization/VisualizationManager`（`supportsVisualization/get/getOrThrow`）、`api/visualization/VisualizationContext`（`instancerProvider/renderOrigin/createEmbedding`）、`api/visual/{Visual,BlockEntityVisual,DynamicVisual,TickableVisual,LightUpdatedVisual,SectionTrackedVisual,ShaderLightVisual}`、`lib/visualization/SimpleBlockEntityVisualizer`（`builder/factory/skipVanillaRender/neverSkipVanillaRender/apply`）、`lib/visual/{AbstractVisual,AbstractBlockEntityVisual,SimpleDynamicVisual,SimpleTickableVisual}`、`api/instance/{Instance,InstanceHandle,InstanceType,InstanceWriter,Instancer,InstancerProvider}`、`api/layout/LayoutBuilder`、`lib/instance/{InstanceTypes,SimpleInstanceType,AbstractInstance,TransformedInstance,ColoredLitOverlayInstance,FlatLit,OrientedInstance}`、`lib/model/Models`、`lib/model/baked/PartialModel`、`lib/material/Materials`、`lib/transform/Affine/TransformStack`、`lib/util/RendererReloadCache`、`impl/visualization/VisualManagerImpl`（`queueAdd/queueRemove/queueUpdate/processQueue/framePlan/tickPlan`）、`impl/visualization/storage/BlockEntityStorage`（`createRaw/willAccept/remove/recreateAll`）、`impl/mixin/visualmanage/{BlockEntityMixin,LevelRendererMixin}`、`neoforge/.../FlywheelNeoForge`（`PartialModelEventHandler` 事件挂载，行 98-99）、`neoforge/lib/model/baked/PartialModelEventHandler`、`backend/engine/uniform/FrameUniforms`、资源 `flywheel/internal/uniforms/frame.glsl`、`flywheel/internal/common.vert`（`_flw_main`）、`flywheel/instance/transformed.vert`、`docs/flywheel/api/{index,glsl-api}.md`、`docs/flywheel/api/stage/vertex.glsl`。

**Create 6.0.11（`ref/create`，commit 0924e93，均为【一手】）：** `build.gradle:132-136`（依赖形态）、`foundation/data/CreateBlockEntityBuilder.java:22,81-115`（注册链）、`AllBlockEntityTypes.java:242-406`（`.visual(...)` 调用面）、`AllPartialModels.java:284-296,323-329`（part 资产约定）、`foundation/render/AllInstanceTypes.java`（ROTATING/SCROLLING/SCROLLING_TRANSFORMED/FLUID 四个自定义 InstanceType）、`content/kinetics/base/{RotatingInstance,OrientedRotatingVisual,KineticBlockEntityVisual}.java`、`content/contraptions/bearing/BearingVisual.java:40-88`、`content/contraptions/bearing/BearingRenderer.java:30`、`assets/create/flywheel/instance/rotating.vert`（顶点着色器全文）、`content/contraptions/psi/PortableStorageInterfaceMovement.java`（`VisualizationManager` 驱动例）。

**MTurrets：** `common/ModBlockEntityTypes.kt:38-40`（`DUO_BLOCK_ENTITY`）、`common/turrets/DuoTurret.kt:23`（`DuoTurretBlockEntity`）、`assets/mturrets/textures/block/duo.png`（现有贴图）、`core/turret/entity/BaseTurretBlockEntity.kt`（BE 同步字段，任务上下文提供，未重读验证行号【上下文来源】）。

## 11. 未能核实

- **`net.createmod.catnip.*` 源码**：`ref/create` 是 Create 单仓浅克隆，不包含 catnip 源码；maven.createmod.net 与 GitHub（`CreakMod/Catnip`、`createmod/Catnip`、org 搜索）均未找到 catnip 仓库/构件（【一手】多次 404）。结论：Create 的 BE 注册**直接用的 Flywheel lib 类**（`CreateBlockEntityBuilder.java:22` import 为证），与 catnip 无关；但 `CatnipServices.PLATFORM.executeOnClientOnly` 内部实现未核实（不影响结论）。catnip 的 `CachedBuffers`/`SuperByteBuffer` 是**回退轨**用的 vanilla 渲染工具，与 Flywheel 无关，MTurrets 回退轨可自行用普通 buffer。
- **`FlwInstance` struct 的精确生成规则**：`common.vert`/`api_impl.glsl` 证实存在 `FlwInstance` 且字段 = layout 字段，但"layout → struct"的生成器源码（`LayoutInterpreter` 或 UBO 生成路径）未逐行读完，属实现细节；对使用方无影响。
- **虚拟 BE（IMPORTANT 式）与 `BlockEntityStorage.willAccept`** 的组合行为：`willAccept` 确认存在空方块检查（已核实代码），但"地图编辑虚拟 BE"在这种管理下的实际表现未在源码内确认，落地 Duo 前应游戏内验证一次。
- **`SimpleDynamicVisual.beginFrame` 的并行调度细节**：`lib/task/*`（`Plan`/`RunnablePlan`）确认存在且 `Simple*` 默认包成 RunnablePlan，但实际并行度/线程模型未深挖（文档声明的互斥与可并行性已引用）。
- Flywheel 各文件**精确行号**：本报告 Flywheel 引用给到文件+成员级（未逐行编号），Create 引用给到行级。

---

- 本报告未运行任何构建/测试/格式化命令；仓库文件除本报告外未做任何改动。