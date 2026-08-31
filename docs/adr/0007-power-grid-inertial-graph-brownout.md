# ADR-0007: 电网——惰性图传播 + FE 单位 + Mindustry 比例棕停，节点零储能

状态：accepted（2026-08 敲定）

电力网络对位 Create 动力图（`RotationPropagator` 范式）：放置/破坏节点时递归染色（网络 id + 源），稳态零 tick 扫描；不做每 tick 相邻扫格。能量单位与对外接口用 FE（NeoForge 原生 `EnergyStorage` capability，零新依赖，可与外部 mod 互通）；Mindustry power 数值 → FE 只是配置换算常数。

供需模型采 Mindustry 语义而非 FE 圈惯例：网络汇总总发电与总拉取，供电不足时全体耗电结构按 ratio 降速（棕停/Brownout），而不是"各自蓄电、放完即停"。`PowerNode` 为纯导线构件、零储能（对齐 Mindustry 源码事实），储能独立为 `Battery` 方块；一期无液网前提下电池先做，液网/电池战术深度后置。

## Considered Options

- **FE 共享大电容**：实现最薄，但丢掉棕停这一玩法层——移植的目的正是它，否决。
- **每 tick 扫格**（Mindustry 原版式）：稳态成本随格数增长，Create 图已示范惰性化，否决。
- **节点兼微储能**（旧词表定义）：否决——与"节点=导线"的 Mindustry 语义冲突，词表两词分立后无此必要。

## 修订 2026-08（打磨阶段）：生产面

正文承诺的"汇总总发电"在一期实现中缺席——`PowerGraph` 只有储能聚合与需求结算（唯一需求方为窑炉，`requestDrain` 带单需求方结算上限的 `ponytail:` 注记）。打磨阶段补上生产面：

- `PowerGraph` 增加生产者集合与每 tick 生产聚合；结算仍走 gameTime 去重的惰性单点，无稳态扫描。
- Mindustry 语义落全：生产先满足需求 → 盈余按比例充入电池（镜像 withdraw 的分摊）→ 缺口先放电池放电 → 仍不足则按 `ratio = 供给/需求` 棕停。
- 两个生产者适配器：#49 Power Source（常量 ≈333,333 FE/t，调试块）与 #56 燃烧发电机（燃料计时生产，20 FE/t）。
- 多需求方公平分摊（原 `ponytail:` 上限）随第二个耗电结构落地时一并处理，不在本修订内。
