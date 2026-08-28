# Create 工程范式调研（面向 MTurrets）

- 调研日期：2026-08-28
- 对象：`ref/create`（Create 6.0.11 / NeoForge 1.21.1，浅克隆 commit `0924e93`，分支 `mc1.21.1/dev`），源码路径均相对 `ref/create/src/main/java/com/simibubi/create/`，行号已抽验（bearing 角度插值、SyncedBlockEntity 两处与正文一致）
- 目标：提炼"代码内容之外"的可移植工程范式——BE 基类、同步、渲染、动画、多方块、性能、注册管线、质量设施。只讲机制，不讲 Create 加了什么方块

## 0. 优先级速览
| 优先 | 模式 | 一句话 | 章节 |
| --- | --- | --- | --- |
| P0 | 旋转角跨包插值 | 现在炮塔 20Hz 阶梯转 + 旋转期每 tick 全量包；改为外推+衰减纠偏、差值超阈才发 | §2.5 |
| P0 | clientPacket 分流同步 | `getUpdateTag` 不再 `saveWithoutMetadata` 全量打包，客户端包只带渲染字段 | §1.1 |
| P0 | 渲染包围盒 | 全仓 0 处 `getRenderBoundingBox` 覆写，炮管伸出单格必被视锥剔除 | §1.4 |
| P0 | 传送带数据模型 | 物品必须是 BE 数据（`beltPosition` 浮点进度）而非 `ItemEntity`；战役规模下实体路线撑不住，且是未来接 Flywheel 的前提 | §4.4 |
| P1 | 惰性 tick 分层 | tick 里只留廉价判定，同步统一走节流通道；卸载/破坏生命周期三分 | §1.3 §1.5 |
| P1 | 邻居 capability 缓存 | `BaseMachineBE`/`PowerNodeBE` 每 tick 裸查邻居；换 `BlockCapabilityCache` + watchdog | §4.2 |
| P1 | 循环音效状态推导 | 声音由客户端状态桶聚合，绝不发 play/stop 包 | §4.5 |
| P1 | Compat 隔离 | Jade 从 implementation 降 compileOnly，`@WailaPlugin` 天然门卫 | §5.3 |
| P2 | 主从多方块 | 2×2 大炮塔：controller 坐标 + capability 代理 + splitMulti | §3.1 |
| P2 | 网络聚合对象 | 电力网学 KineticNetwork：networkId + dirty 惰性重算，不每 tick 互推 | §3.2 |
| P2 | 注册协调函数 | "加一个炮台改五个文件"压回一处调用 | §5.1 |
| P2 | GameTest helper | 类型化访问器（不符即 fail）、时间常量、assertInRange | §6.1 |
| **已决** | 渲染基座 = Flywheel，移除 GeckoLib | 传送带 + 战役规模坐实；GeckoLib 使用面≈0（仅 Duo、无关键帧）。见 ADR-0002 | §2.4 §7 |
| 暂缓 | Ponder / Crowdin / RuntimeDataGenerator | 规模或人力不匹配，见各节 | — |

---

## 1. BlockEntity 基类与网络同步

### 1.1 `clientPacket` 分流：存档数据与即时同步共用一套序列化

**机制**：SmartBlockEntity 把 vanilla 的 `saveAdditional`/`loadAdditional` 拦截成 `write(tag, registries, boolean clientPacket)` / `read(tag, registries, boolean clientPacket)`（`foundation/blockEntity/SmartBlockEntity.java:93-96,110-120`）。`clientPacket=false` 走存档；`clientPacket=true` 走 `writeClient`/`readClient`（`SyncedBlockEntity.java:27-51`，即 `getUpdateTag`→`writeClient`、`onDataPacket`/`handleUpdateTag`→`readClient`）。同一份字段按通道决定写不写：SawBlockEntity 的 `PlayEvent` 只在 clientPacket 分支写入并**写后清零**（`content/kinetics/saw/SawBlockEntity.java:118-137`）——不落盘、随下一次更新包送达、播一次音效，实现"一次性事件随包捎带"。更新包装配时机：`sendData()`→`ServerChunkCache.blockChanged(pos)`（`SyncedBlockEntity.java:59-62`）把 BE 标脏，区块追踪系统对**正在追踪该区块的玩家**广播 `getUpdatePacket()`；`notifyUpdate()` = `setChanged()+sendData()`（L64-67）。

**MTurrets 借用**：`BaseTurretBlockEntity.getUpdateTag` 直接返回 `saveWithoutMetadata`（`core/turret/entity/BaseTurretBlockEntity.kt:316-318`），每次 `syncData()` 把能量+流体+物品+旋转+弹药全量打包，`saveAdditional` 又重复写旋转字段。应分流：客户端包只写渲染需要的 `targetRotation`/`targetPitch`（客户端只做角度插值，见 §2.5），其余留在存档通道。`MTurretsModBlockEntity.syncData()`（:74-77）本身已是正确组合，保留。

### 1.2 一次性事件 RPC：`blockEvent` 是死路，包捎带 + 带校验的 C2S 才是活路

**机制**：vanilla `Level.blockEvent`→`Block.onEventReceived` 是教科书方案，但**当前 Create 源码 0 处调用**（全仓 grep 仅一个空桩）。实际用：(a) `level.levelEvent(...)` 播全局音效/粒子；(b) BE 专用一次性动画/音效复用 §1.1 的 clientPacket 捎带（Saw 的 PlayEvent），天然只发给追踪玩家且零新增包类型。反向 C2S 收敛为一个基类 `BlockEntityConfigurationPacket`（`foundation/networking/BlockEntityConfigurationPacket.java:14-39`）：handle() 先做 spectator/冒险模式/距离(20 格)/方块已加载检查再 `applySettings`。

**MTurrets 借用**：开火音效/枪口火焰用 `level.levelEvent`，或给 BE 加一次性 `fireTick` 字段随包捎带、客户端消费后清零。玩家换弹/改目标的 C2S 包照抄校验序列（`player.canInteractWithBlock(pos, 20)` 先行），用 NeoForge `CustomPacketPayload` + `PayloadRegistrar`。

### 1.3 惰性 tick 分层

**机制**：`IBE.getTicker` 默认给 `SmartBlockEntityTicker`；`SmartBlockEntity.tick()`（:74-86）结构：首 tick `initialize()` → 倒数到期才 `lazyTick()`（默认 10 tick）→ 行为组件各自再节流。重活下一层：`KineticBlockEntity.tick()`（:97-125）用 `needsSpeedUpdate()` 短路、`validationCountdown` 降频校验、客户端分支提前 return——**同一 tick 方法按端裁剪**。无需 tick 的方块 `getTicker` 返回 null（CopycatBlock）。跨 tick 消息合并：`sendDataLazily` 的 `syncCooldown`+`queuedSync`（`behaviour/fluid/SmartFluidTankBehaviour.java:115-136`）。

**MTurrets 借用**：`tickServer` 里 `updateRotation` **旋转中每 tick `setChanged()+syncData()`**（`BaseTurretBlockEntity.kt:200-205`）＝每 tick 一个全量包。把 `onEnergyChanged` 已有的 5-tick 节流（`MTurretsModBlockEntity.kt:197-204`）推广为唯一同步通道：角度差超阈值才发、否则并入周期 sync。

### 1.4 渲染包围盒缓存

**机制**：`CachedRenderBBBlockEntity`（:18-32）缓存 `createRenderBoundingBox()`，null 才重建，变更点显式失效。原因：渲染器**每帧**调它做视锥剔除，逐帧 new AABB 是热路径分配；几何超单格的 BE 不扩盒就会被裁掉。

**MTurrets 借用**：全仓 0 处覆写（grep 验证）。炮塔模型（基座+转头+伸出的炮管）超出单格，靠近就会消失/闪烁。`MTurretsModBlockEntity` 加缓存字段 + 子类按模型尺寸给扩展盒（如 `AABB(pos).inflate(1.5, 1.5, 1.5)`，2×2 大炮塔扩到覆盖 footprint）。GeckoLib 读它做剔除，纯 BE 层改动，无需 Flywheel。

### 1.5 生命周期三分 + 能力解绑

**机制**：`chunkUnloaded` 标志区分"区块卸载"与"被破坏"：卸载**不**走 `remove()`（网络/传动关系应保留），真破坏由 `IBE.onRemove` 先调 `destroy()` 再 `removeBlockEntity`——破坏/替换/卸载三路径分明。`ItemHandlerContainer`（:9-104）把 `IItemHandlerModifiable` 适配成 vanilla `Container`，让漏斗/发射器用原版逻辑操作自定义库存。

**MTurrets 借用**：现在卸载/破坏都走默认路径，无解绑钩子。补 `onChunkUnloaded` + `setRemoved`/`destroy` 区分（尤其将来有网络关系后）；`ItemCapabilityImpl` 若实现 `IItemHandlerModifiable`，包一层 Container 即让 Kiln 直接对接漏斗。`IMergeableBE`（多块合并契约）当前 YAGNI。

**依赖标注**：§1 全部 vanilla/NeoForge 可移植；`.renderer()` 链式注册依赖 Registrate，等价物是 `EntityRenderersEvent.RegisterRenderers`；`visual()` 是 Flywheel 专属，勿模仿。

---

## 2. 渲染与动画

### 2.1 一个通用 BER + final 守卫（纯 vanilla 可移植）

**机制**：Create 不为每个 BE 写专属渲染器；几十种 BE 注册同一个 `SmartBlockEntityRenderer`，渲染逻辑挂在行为组件上。真正可移植的骨架是 `SafeBlockEntityRenderer`（:23-37）：`render()` 是 final，先过 `isInvalid` 守卫（无 level / 方块是空气→不渲染），再分派 `renderSafe`，剔除盒转发 §1.4——所有 BER 免费获得防崩溃。

**MTurrets 借用**：炮塔几何走 GeckoLib 骨骼，行为化渲染用不上；但"final render + isInvalid + 缓存剔除盒"值得做成我们的渲染基类（现在各 renderer 重复 hasLevel 检查）。虚拟世界渲染（contraption 搬方块，`BlockEntityRenderHelper:44-86`）留给未来的"可搬运结构"需求。

### 2.2 PartialModel：一份几何 + 每帧一个变换（需要 Flywheel）

**机制**：可动部件是独立子 JSON（`AllPartialModels.java:24-123`），Flywheel `PartialModel` 惰性烘焙成 `BakedModel`，同一份顶点跨 BE 复用（SHAFT 服务马达/轴承/转速表）；每帧只改 `rotateCentered/translate/light/shiftUVScrolling` 参数，不重查不重烘。

**MTurrets 借用**：GeckoLib 骨骼就是这个思想的现成等价物，勿重造。可迁移的抽象：**烘焙几何缓存一次、每帧只推变换与打包光照**；若做弹丸批渲染，`TemplateMesh` 的紧凑顶点布局（9 int/顶点）是参照。

### 2.3 SpriteShift + UV 重映射（纯 vanilla 可移植）

**机制**：连接纹理不是生成几何，是烘焙期 UV 重映射：`CTModel` 扫邻居算瓦片索引存进 `ModelData`，`getQuads` 时按索引把 quad UV 改写进目标贴图的瓦片网格（`foundation/block/connected/`）。动画走"长条滚动图 + UV 平移"（pulley 的 `scrollCoil`），同样不重烘。

**MTurrets 借用**：Kiln 的高温外观可用 `BakedModelWrapperWithData + ModelData` 按 BE 数据重映射到"烧红/发光"瓦片，纯 NeoForge API，比加 blockstate 或换纹理轻。电力连线的"能量流动"动画同理用滚动 UV，别每帧换纹理。

### 2.4 Flywheel Visual：旋转在 GPU 顶点着色器里（需要 Flywheel）

**机制**：`AllInstanceTypes.ROTATING` 把 speed/offset/axis 声明进实例布局，`rotating.vert` 用 `offset + renderSeconds * speed` 算每帧角度——**CPU 侧完全不查 BE**。棋盘式 `rotationOffset` 让相邻轴相位错开（位置奇偶哈希，一次算好）。`ServerSpeedProvider`（:17-76）服务端每 N tick 广播、客户端 `LerpedFloat` 指数逼近真实刻率倍率，统一修正所有客户端速度计算。无 Flywheel 时回退 `KineticBlockEntityRenderer`，同一数学的 CPU 版。

**MTurrets 借用**：~~不引 Flywheel~~ **已修订**：传送带 + 战役规模确定要做，Flywheel 提上日程，见文末"Flywheel 决策"。两个纯 vanilla 可抄点无论引不引都成立：(a) `rotationOffset` 相位错开——同位置哈希给多炮塔/双管炮的动画相位，避免全同相的呆板；(b) `ServerSpeedProvider` 的"周期广播+指数逼近"修正器（约 30 行），服务器 tick 波动时我们的弹道预判/动画节奏会系统性偏移，这个修正器正好治它。

### 2.5 跨包角度插值：外推 + 衰减纠偏（纯 vanilla 可移植，P0）

**机制**：`MechanicalBearingBlockEntity` 是炮塔的直接类比。服务端包带 `Angle`（:77-84）；客户端 `read()` **不采纳新角度**：算出与旧值的最短角差记入 `clientAngleDiff`，然后把 angle 回退（:93-105）；渲染用 `Mth.lerp(partialTicks, angle, angle + angularSpeed)`（:111-120），`angularSpeed` = 同步角速度 × `ServerSpeedProvider.get()` + `clientAngleDiff / 3f`（:142-151），`clientAngleDiff` 每刻 /2 指数衰减。即：客户端沿最后已知速度**外推**，把包间角差当小修正慢慢吃掉——20Hz 包也能渲出 60fps 平滑旋转，且不跳变、不漂移。

**MTurrets 借用**：`DuoRenderer.preRender` 直接烘焙包快照 `currentRotation`、无 partialTick——炮塔 20Hz 阶梯运动。闭环改造三件：BE 记 `prevRotation` + 包到算最短角差不覆盖；渲染侧 `Mth.lerp(partialTick, ...)` + 按 `rotateSpeed` 外推；发包条件从"转就发"改为"差超阈才发"。注意 yaw 跨 ±180° 必须走 `Mth.wrapDegrees` 最短角插值，现在渲染端没有，快速转体时会绕远路。

---

## 3. 多方块与网络图

### 3.1 主从 BE：controller 坐标 + capability 代理

**机制**：一个多方块每格都是同一 BE 类，只有 controller 存真状态，附属格只存 `controller` 坐标，`getControllerBE()` 沿坐标找回主 BE（`FluidTankBlockEntity.java:148-152`）。成形/分裂是**事件驱动**：放置 `onPlace→ConnectivityHandler.formMulti`（BFS 扫最大矩形 footprint，内容并入主 BE），破坏 `onRemove→splitMulti`（按容量分回内容、`invalidateCapabilities`、可选重连）。capability 只挂主 BE，附属格返回"转发到 controller 的活代理"（controller 不存在→null 哨兵，防悬空引用）。渲染盒只有主 BE 按 footprint 扩展。NBT 只有主 BE 读写尺寸/内容；"Uninitialized" 标记让加载后下一 tick 重跑成形。选格高亮 `BigOutlines` 依赖 Create 混入——vanilla 等价：2×2 炮塔直接覆写 `getInteractionShape`/`getShape` 返回合并 VoxelShape，拾取/选中/use 天然落到整机，零依赖。

**MTurrets 借用（2×2 大炮塔）**：四格同一个 `TurretBlockEntity`；能量/弹夹/瞄准角/动画相位只存 controller（约定最低 XYZ 格）；capability 注册统一返回 controller 代理；破坏任一格 → 先掉该格应掉物品 → split → 剩余格可独立成形的降级为 1×1；动画只有 controller 一个实例在跑。成形规则写简化版（固定 2×2，同类同朝向同平面），不需要泛型宽度扫描。

### 3.2 每网络聚合 × 每 BE 局部引用 × dirty 惰性重算

**机制**：BE 只持久化 `network: Long / source: BlockPos / speed: float`；聚合值（总承载/总负荷）在 `KineticNetwork` 上，成员表存每个成员的**基础**贡献（实际值 = 基础 × |转速|）。**不每 tick 重算**：`networkDirty` 置位 → 该 BE 下一次 tick 触发一次 `network.updateNetwork()` 全员 sync。区块卸载的成员贡献不丢：BE NBT 里另存 `lastStressApplied` 等，重载时 `initFromNetwork` 恢复聚合。每 N tick 的 `validateKinetics` 只做局部自愈（查 source 还在否），不扫全图。负荷数值配置驱动（`BlockStressValues` 按 Block 注册 supplier）。

**MTurrets 借用（电力网）**：节点 BE 只存 `networkId`；`PowerNetwork` 对象聚合 `totalStored/totalCapacity`（`Map<Level, Map<Long, Network>>` 两个 HashMap，纯 vanilla）。节点 NBT 存自己的贡献值防卸载丢失。`IEnergyCapability.onEnergyChanged()` 就是天然 dirty 回调——接到"置位 → 下 tick 重算聚合"上。现在 `PowerNodeBlockEntity.kt` 每 tick 逐节点互推 + 每 100 tick 全量 validate（:60-62,84-106），降级为兜底。

### 3.3 传播 = 拓扑变化时一次 BFS + 仲裁，稳态零计算

**机制**：`RotationPropagator.handleAdded→propagateNewSource` 只在放置/拆除时跑：邻居算传动比后三方仲裁（反向冲突→物理拆块；同向"快者覆盖慢者"；同网络 id 视为环不覆盖）。拆块只清空"以自己为 source"的子树，收集仍挂源的邻居作候选电源重跑。**稳态下每个 BE 就是上次传播留下的 source+speed 快照**，tick 只做局部校验。

**MTurrets 借用**：能量流向规则（高储量→低储量）保留，但**仲裁集中到一个纯函数** `canConnect(stateA,posA,stateB,posB)`，别每节点独立循环对推；放置只扫 `MAX_CONNECTION_DISTANCE` 邻域并边，拆除通知邻居局部重源。

---

## 4. 性能与 IO

### 4.1 RecipeTrie：按键索引配方

**机制**：两阶段。`RecipeFinder` 按机器实例缓存"静态过滤后的配方列表"（Guava Cache，`ResourceManagerReloadListener` 重载时 `invalidateAll`）；`RecipeTrieFinder` 再把配方 ingredients 正则化成有序 int 数组键插入 trie，查询只涉及库存变体，子集 DFS 复杂度 `O(min(children,pool))`。调用方保留线性遍历兜底。

**MTurrets 借用**：Kiln 改 datapack 配方驱动时抄两层结构（缓存 + 重载监听失效，不定时清）。同构问题在索敌：`findTarget` 每 10 tick AABB 全扫——结果缓存方向已对，缺的是**事件失效**（目标死亡/离开区块即清，而非等下个定时器），可省大量空扫描。

### 4.2 邻居 capability 缓存 + 三段失效信号

**机制**：`CapManipulationBehaviourBase` 只在三种时机重查目标 capability：① 方块更新事件精确命中目标 pos（置 null、下 tick 重查）；② lazyTick（5 tick）仅在 null 时自愈；③ `gameTime % 64` watchdog 强制重查，覆盖"同 tick 换 BE 没发事件"等静默变化。持有侧对偶：换 handler 实例后主动 `invalidateCapabilities()` 通知所有缓存方。

**MTurrets 借用**：这是**我们最痛的裸查**——`BaseMachineBE.tryAutoEject` 与 `PowerNodeBlockEntity` 每 tick 每方向 `level.getCapability(...)`（`BaseMachineBE.kt:146-149`、`PowerNodeBlockEntity.kt:100-104`）。NeoForge 原生 `Level.getCapabilityCache()`（`BlockCapabilityCache`）自带 ①②两档失效，直接用；③ watchdog 抄 Create。

### 4.3 TickBasedCache：tick 时间戳 TTL

**机制**：按全局 tick 计数过期（非墙钟——服务器卡顿时墙钟行为错误）；`resetTimerOnAccess` 区分滑动窗口与固定防抖；外层短 TTL 包内层长 TTL 的嵌套充当自动 GC。

**MTurrets 借用**：我们已在手搓同款（`targetTimer`/`lastEnergySyncTick`）。增量教训：时间戳直接用 `level.gameTime`；`onEnergyChanged` 的 5-tick 节流语义（突发只跟一次 sync、持续才节流）是对的，别改成固定计数吞掉突发。

### 4.4 BeltInventory：物品是数据不是实体

**机制**：传送带物品 = controller 里的 `List<TransportedItemStack>`（ItemStack + `beltPosition`/`prevBeltPosition` 浮点进度），每 tick 一次有序遍历推进，跨 tick 变更走 `toInsert/toRemove` 延迟批次保遍历稳定。**实体只在出口存在**（`eject()` 是全类唯一 new ItemEntity）。渲染客户端直接读同一数据插值。

**MTurrets 借用（已升级为 P0 设计约束）**：传送带确定要做，那么**从第一行代码起物品就必须是 BE 数据（`beltPosition`+`prevBeltPosition` 浮点进度对），不是 `ItemEntity`**。这是唯一能让"战役规模几百格产线"活下来的模型：实体路线每物品一份 AABB/碰撞/拾取/存档开销，且永远无法批渲染；数据化后推进是每 run 段一次 O(n) 遍历、渲染客户端读同一数据插值（§2.4 的 visual 管线的前提）、NBT 随 BE 走。Create 的 belt 网络 = 段 BE + controller 收口（`BeltBlockEntity.java:392-407`），照 §3.1 主从模式做。存储侧现状（`ItemCapabilityImpl`=Array）已是数据化，方向对。

### 4.5 SoundScapes：循环音效由状态推导、按桶聚合

**机制**：循环音**不发 play/stop 包**。运转中的 BE 在客户端 tick 把 `(group,pos,pitch)` 登记进静态计数（速度>0 才登记，服务端开销为零——活跃度本来就是同步状态）；聚合器每 5 tick 按 `(group,pitch桶)` 保证至多一个音源，多台共享并定位到均值点，计数归零自动 stop。拆机/卸载/断连全部 5 tick 内自愈。

**MTurrets 借用**：炮塔旋转/充能音照抄：客户端从同步状态推导"该发声"，每档转速一个 looping sound instance，桶聚合 + 引用计数。纪律：**不转不登记**，闲置塔零成本。这从根上避开循环音泄漏成"静音幻听"的经典坑。

### 4.6 VirtualRenderWorld（需 Flywheel，仅记机制）

假 `Level`（Map<BlockPos,BlockState> + section 计数驱动真实光照引擎）让原版渲染管线渲染脱机结构。MTurrets 做射程预览 ghost 不需要它：`Map + BlockRenderDispatcher` 逐状态渲染即可；真要复用光照/面剔除时直接引 Flywheel。

---

## 5. 注册与数据管线

### 5.1 每个内容一条链：漏一步"看得见"

**机制**：Registrate 把方块+物品+BE+blockstate+tag+lang+loot 收进一条链式调用，漏掉的产物在生成的资源里**直接缺失**（可 diff 发现），而非分文件"无声没注册"。`validBlocks` 是方块↔BE 唯一事实源（一个 BE 可服务多个方块）；方块侧 `newBlockEntity` 由 `IBE` default 代写，不手抄。

**MTurrets 借用**：现状"一件事五处写"：加一个炮台要改 `ModBlocks.kt`+`ModItems.kt`+`ModBlockEntityTypes.kt`+`DataGen.kt`(lang+blockstate)+手工 `zh_cn.json`，且 BE↔方块链接写了两遍（Builder 的 blocks 参数 + 各方块的 `newBlockEntity`）。不引 Registrate 的等效：`registerTurret(id, ...)` 协调函数一次调 DeferredRegister 并收集 lang 键/模型描述符，DataGen 消费该列表；`newBlockEntity` 收进 `BaseTurretBlock` 默认实现删掉各副本。`Materials.kt` 枚举驱动注册已经是我们的亮点（注册/datagen/lang 循环同一枚举天然零漂移），协调函数沿用此手法。

### 5.2 datagen → 源语言 → 翻译管线

**机制**：en_us 是构建产物（每个注册项自动贡献 lang 键），提交后作 Crowdin 源语言，社区语言回灌成手工文件；CI 只 gate generated 新鲜度，**翻译文件本质上允许落后**——它坏显示不坏功能。取舍：源语言必须程序化，翻译允许手工慢。

**MTurrets 借用**：en_us 生成 + CI diff 门禁已达标。缺口：zh_cn 纯手工且无键同步校验（当前 55/55 一致纯属运气，加新塔忘改 zh_cn 时 CI 不拦）。廉价修法：CI 加一行 `set(zh_cn)-set(en_us)` 非空即 fail，或 zh_cn 也进 LanguageProvider 双语言分支。

### 5.3 RuntimeDataGenerator 的边界 + Compat 隔离

**机制**：运行时从注册表推导内容（对任何 mod 的木头生成全套切割配方）只用于"规则机械+随注册表增长+单条价值低"的交叉内容；自家设计的配方仍走 datagen 明文化。**Compat 隔离**三层：`Mods` 枚举快照加载状态；注册门卫传**双 Supplier**（`executeIfInstalled(() -> Proxy::registerWithDependency)`——依赖类只出现在方法引用里，mod 缺失永不解析，不触 NoClassDefFoundError）；JEI/Jade 插件走宿主自己的注解发现机制，宿主代码零引用天然隔离。构建侧配合 compileOnly + 条件源集排除。

**MTurrets 借用**：Kiln 配方量小且是设计内容，走 datagen `RecipeProvider`，**不要**学运行时推导。Compat 立刻可做：`build.gradle.kts` 把 Jade 降 `compileOnly` + mods.toml 去掉必装约束——`@WailaPlugin` 发现机制保证类只在 Jade 在场时加载，零代码改动；未来软依赖统一走 `Mods.kt` 枚举 + 双 Supplier。

### 5.4 "常量注册表"文件风格

`AllTags`/`AllSoundEvents`/`AllShapes`：单文件持有横切面全部常量，枚举名即 id，消费方一行引用，改一处编译期全绿。MTurrets 缺：turret 方块没挂 `MINEABLE_PICKAXE` 等挖掘标签（采集时间异常、工具行为不对）——建 `ModTags` 集中挂；将来做炮声时照 `SoundEntry` 把 sound json + subtitle lang + 注册合一。

---

## 6. 质量与运维设施

### 6.1 GameTest：会 fail 的访问器 + 端到端黑盒

**机制**：`CreateGameTestHelper extends GameTestHelper` 提供"类型不符即 fail"的 `getBlockEntity(type,pos)`、按内容断言的 `assertContainerContains`、`assertInRange`（防浮点抖动）、`whenSecondsPassed` 时间分步；回归测试按 issue 编号命名（`issue9615_efficientDeployers`），断言只看外部可观察结果。注册期校验测试签名（static/void/参数类型/必带 template），杜绝"没在断言"的静默通过。

**MTurrets 借用**：我们的 GameTests 已坚持外部行为断言（伤害/能量/产出），方向正确。立刻采纳：类型化 BE 访问器（替掉 `as? ArcTurretBlockEntity` 空安全 cast——cast 失败现在静默跳过，应 fail）、时间常量（现在裸 `timeoutTicks=300`）、`assertInRange`。真实 `.nbt` 结构模板暂缓，等多方块场景再上。

### 6.2 配置：分层 + 自动同步防"双端数值分叉"

**机制**：CLIENT/COMMON/SERVER 三层；NeoForge 自动把 SERVER 配置下发客户端，所以客户端**显示/预测逻辑直接读服务端权威值**（JEI 按服务端规则决定显示哪些配方）。失败模式：不同步 → 客户端预测与服务端裁决分叉。注意重载只对新加入玩家生效。

**MTurrets 借用**：`TurretConfig` 里"服务端权威但客户端要显示/预测"的数值（耗能速率、射速——GeckoLib 动画帧率消费它）挪进 SERVER 型配置注册即自动同步；Config.kt 按 Create 的分组+注释纪律重写，官方 ConfigurationScreen 免费获得分组 UI。

### 6.3 `/create debuginfo`：issue 报告的数据源

双端 section 树（mod 版本+git commit/显卡/系统/其他 mod），每条 provider 异常被捕获写进 dump 而非炸掉整命令，S2C 包拼服务端段，客户端汇总后**直接以 GitHub `<details>` 折叠格式写剪贴板**——报障方零门槛、修复方零追问。MTurrets 精简版：客户端命令 dump mod 版本+配置+集成 mod 列表即可；电力网拓扑成型后把节点连接表塞进去（配 §5.1 的 git-hash 注入）。

### 6.4 Ponder（暂缓）

游戏内文档 = 独立 ponder 库 + 场景 DSL + 一次性 PonderLevel 回放。炮塔交互面小（放块→供能→开火），聊天栏文本足够；等多步流程（电网布线、窑炉产线）再评估。

---

## 7. Flywheel / GeckoLib 决策（已敲定 → ADR-0002）

背景：确认要做传送带，且预期"超级大范围的战役规模"（几百上千个持续运动的部件：带面滚动、炮塔群、弹雨）。逐 BER 每帧提交撑不住该量级，GPU 实例化从"可选"变成"必需"。

**核实事实**：Flywheel 现为 MIT（github.com/Engine-Room/Flywheel），与 GPL-3.0 无传染冲突；1.21.1 NeoForge 版存在且被同版本 Create 使用（`dev.engine-room.flywheel:flywheel-neoforge-1.21.1:1.0.6`，maven.createmod.net），按 Create 先例 jarJar 内嵌、玩家免装。

**GeckoLib 使用面核实为≈0**（当前代码）：仅 Duo 一个渲染器使用；`duo.animation.json` 的 animations 为空；`registerControllers` 空实现；模型为 11 骨骼 31 cube、零 mesh——纯立方体，Blockbench 重导出 Java block model 无形状损失；旋转是 `DuoRenderer.preRender` 手动 `setRotY/setRotX`。关键帧播放/Molang/控制器这三个卖点全部未用。

**决定**：Flywheel 为唯一动画方块渲染基座，移除 GeckoLib（净 BOTH 依赖不增）。动画原则：**状态 → 变换参数的代码驱动，不引入动画资产格式**（关键帧 JSON / Molang）——CPU 回退轨在渲染器内做 PoseStack 矩阵变换（含跨包角度插值），GPU 轨写实例字段由着色器推进；两轨并存是 Create 验证过的标准形态（`KineticBlockEntityRenderer:50`），不是过渡债。

**对弹雨的提醒**：战役规模的弹幕瓶颈在实体侧不在渲染侧——实例化救不了每 tick 实体碰撞与同步；参照 Mindustry 池化思路做投射物复用，或激光类走 §2.3 滚动 UV 不造实体。

完整决策与理由见 `docs/adr/0002-rendering-stack-flywheel.md`（取代 ADR-0001 §3 的 GeckoLib 条款）。

---

## 附：本报告未覆盖但值得留意的

- 应力(stress)的**数值设计**（每机器 impact/capacity 表）是玩法平衡，不是范式，未展开。
- contraption（动态结构）整体是 Create 最大子系统，MTurrets 无对应需求，全部跳过。
- `ref/create` 是浅克隆，git 历史/PR 讨论不可考；本报告全部基于工作树源码。
