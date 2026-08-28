# ADR-0007: 电网——惰性图传播 + FE 单位 + Mindustry 比例棕停，节点零储能

状态：accepted（2026-08 敲定）

电力网络对位 Create 动力图（`RotationPropagator` 范式）：放置/破坏节点时递归染色（网络 id + 源），稳态零 tick 扫描；不做每 tick 相邻扫格。能量单位与对外接口用 FE（NeoForge 原生 `EnergyStorage` capability，零新依赖，可与外部 mod 互通）；Mindustry power 数值 → FE 只是配置换算常数。

供需模型采 Mindustry 语义而非 FE 圈惯例：网络汇总总发电与总拉取，供电不足时全体耗电结构按 ratio 降速（棕停/Brownout），而不是"各自蓄电、放完即停"。`PowerNode` 为纯导线构件、零储能（对齐 Mindustry 源码事实），储能独立为 `Battery` 方块；一期无液网前提下电池先做，液网/电池战术深度后置。

## Considered Options

- **FE 共享大电容**：实现最薄，但丢掉棕停这一玩法层——移植的目的正是它，否决。
- **每 tick 扫格**（Mindustry 原版式）：稳态成本随格数增长，Create 图已示范惰性化，否决。
- **节点兼微储能**（旧词表定义）：否决——与"节点=导线"的 Mindustry 语义冲突，词表两词分立后无此必要。
