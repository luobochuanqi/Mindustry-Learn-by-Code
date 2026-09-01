# ADR-0007: 电网——惰性图传播 + FE 单位 + Mindustry 比例棕停，节点零储能

状态：accepted（2026-08 敲定）

电力网络对位 Create 动力图（`RotationPropagator` 范式）：放置/破坏节点时递归染色（网络 id + 源），稳态零 tick 扫描；不做每 tick 相邻扫格。能量单位与对外接口用 FE（NeoForge 原生 `EnergyStorage` capability，零新依赖，可与外部 mod 互通）；Mindustry power 数值 → FE 只是配置换算常数。

供需模型采 Mindustry 语义而非 FE 圈惯例：网络汇总总发电与总拉取，供电不足时全体耗电结构按 ratio 降速（棕停/Brownout），而不是"各自蓄电、放完即停"。`PowerNode` 为纯导线构件、零储能（对齐 Mindustry 源码事实），储能独立为 `Battery` 方块；一期无液网前提下电池先做，液网/电池战术深度后置。

## Considered Options

- **FE 共享大电容**：实现最薄，但丢掉棕停这一玩法层——移植的目的正是它，否决。
- **每 tick 扫格**（Mindustry 原版式）：稳态成本随格数增长，Create 图已示范惰性化，否决。
- **节点兼微储能**（旧词表定义）：否决——与"节点=导线"的 Mindustry 语义冲突，词表两词分立后无此必要。

## 修订 2026-08（打磨阶段）：生产面

正文承诺的"汇总总发电"在一期实现中缺席——`PowerGraph` 只有储能聚合与需求结算（唯一需求方为窑炉，`requestDrain` 带单需求方结算上限的 `ponytail:` 注记）。打磨阶段（#49）补上生产面：

- **生产是成员属性，结算点聚合**：`PowerMemberBE.productionPerTick`（生产者覆写、非生产者恒 0），`PowerGraph` 每 tick 首个申报点（生产或需求，谁先 tick 谁触发）一次性聚合全部成员产量——非事件流，故生产者/需求方 tick 顺序无关。结算仍走 gameTime 去重的惰性单点，无稳态扫描。
- **串行结算（Mindustry 语义，逐行核过上游 `PowerGraph.update`）**：生产先满足需求（`requestDrain` 按"生产余量优先、电池池补位"抽取）→ 生产超出需求的盈余按**各电池空余容量比例**充入电池（末位兜尾，镜像 `withdraw` 的确定性策略；满电池分到 0）→ 未被吸收的生产本 tick 末消失（不结转）。与上游 `chargeBatteries` 的 `(1-status)` 空余口径一致；上游前置的 `charged` flag 仅影响"零需求零供给"特判的 coverage 归 0，串行账本下该场景 ratio 天然为 1，不需移植。
- **生产量 Int 精确**：#49 Power Source = 常量 333,320 FE/t（上游 `powerProduction = 1_000_000/60` 整型截断 16,666 × 20 FE）；#56 燃烧发电机 = 燃料计时生产（20 FE/t）。全链路沿用 `PowerGraph` 全 Int 账本，无浮点。
- **窑炉本地储能 10,000 → 500 FE（一轮配方能耗）**：旧值是隐式大电池，违反本 ADR "节点零储能、电池储能" 模型（Mindustry 非缓冲建筑只存 `status×1`）。500 覆盖单轮，本地缓冲先于电网消耗，保留对外能量 capability 使外部 mod FE 仍可注入。
- 多需求方公平分摊（原 `ponytail:` 上限）随第二个耗电结构落地时一并处理，不在本修订内。
