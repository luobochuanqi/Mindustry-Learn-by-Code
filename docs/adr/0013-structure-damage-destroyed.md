# ADR-0013: 结构伤害与 destroyed 结算

状态：accepted（2026-09-08，#82 定稿）

为支持单位（#79/#80/#81）拆玩家建筑，引入**结构伤害**：敌方单位子弹命中结构 → 扣结构 Health → 归零触发 destroyed 结算。本 ADR 把一期 #58 划给二期 #34 的「Health 结算/伤害路径」骨架提前落地（仅开**敌方单位子弹打结构**一条口；玩家/爆炸等其他伤害来源仍属二期 #34）。

## 扣血入口

锚点 BE 基类 **`BlueprintAnchor`** 增加通用方法 `hurtStructure(amount: Float, source: DamageSource)`：
- 一处收口，非 TurretBE 专属——炮台/窑炉/钻头/发电机/电池等全部锚点 BE 继承即可扣血。
- 现状：Health 只存在锚点 BE（`TurretBE.health: Int`，save-only，从不扣减），`contentsToScatter(destroyed: Boolean)` 已在 destroyed 时返回空列表（#82 前已预留）。

## 命中识别

命中判定 = 被撞方块是 `BlueprintAnchorBlock` / `StructuralBlock` 家族成员 → 解码 offset 定位锚点 → 调 `hurtStructure`。
- 对现有所有结构生效，零注册表；成员命中自然路由到锚点（同 `onRemove` 的成员解码逻辑）。
- 定义"可打结构" = 锚点 BE 是 `BlueprintAnchor` 的结构。

## 伤害源

新 DamageType **`structure_damage`**（mod 注册）。
- 归属：Flare 子弹 `mobAttack(null)`（无玩家；Flare 是构造体，Mindustry 拆除无击杀者/掉落——与 MC `lastHurtByPlayer` 机制无关，结构非 Entity）。
- 不改现有炮台子弹伤害源（炮台不伤结构，不对称，规划期定案）。
- **击杀归属明确不做**：Flare 拆结构不产生掉落/经验/击杀消息（Mindustry 语义 + MC 结构非 Entity 无 lastHurtByPlayer）。

## 护甲

本期全结构 `armor=0`（Mindustry `Block.armor` 默认 0，flare 弹 `pierceArmor=false`）→ 伤害 `= max(amount−0, 0.1·amount) = amount`。**保底公式 `max(d-a, 0.1·a)` 写入实现**（research §5.1），架构留 armor 字段供未来。

## 子弹侧

`BulletType` 增加 `collidesStructure: Boolean`（默认 false）。
- Flare 弹置 true：撞结构 → 命中点调 `hurtStructure`；撞地形仍消失（忠实 Mindustry，规划期定案）。
- 炮台子弹保持 false（不对称，现状不变）。
- 不新建子弹实体双轨（一个开关位足够）。

## destroyed 结算

`BlueprintAnchor` 增加 `destroyStructure(level, pos)`：
- 复用 `onRemove` 的成员清扫（归属校验避免误拆邻居）。
- `contentsToScatter(destroyed=true)` 已返回空 → 不掉内容物。
- 锚点不 dropSelf（战斗摧毁无玩家收获）。
- 表现 = **裂纹渐进（#42 MultiPosDestructionHandler）→ 归零时着火 + 短爆点 FX（#62 billboard 基建）→ 整结构移除**。

## Health 同步

伤害扣血后 `syncData()`（同 #61 tryLoadAmmo 惯例）→ 客户端读到实时 Health、裂纹渐进；`health ≤ 0` 服务端 `destroyStructure()` + 客户端回收移除。裂纹阶段进度走既有 MultiPosDestructionHandler。

## 可打范围

- 定义：所有 `BlueprintAnchor` 结构可打（炮台/窑炉/钻头/发电机/电池/PowerNode）。发电机优先为 #84 目标映射的事，本 ADR 只定"可打"。
- **例外：Power Source 调试块免疫**（Q4 修订，规划期"全部可打"缩为"除 Power Source 外全部"）——保留创造实验面。

## GameTest

测试留执行票，本 ADR 定清单：
1. Flare 弹命中炮台 → Health 扣减且结构存活。
2. Health 归零 → 全结构格变空气且无内容掉落。
3. 成员命中路由到锚点（打成员格扣同一 Health）。
4. 炮台弹不伤结构（不对称回归）。
5. red-proof（人为回退扣血 → 用例变红）。

## Considered Options

- **只 TurretBE 加 damage**：否决——其他结构打不了，与"全结构可打"矛盾。
- **逐个注册可打方块**：否决——新结构漏注册就不受伤害。
- **复用 mobAttack 不带新类型**：否决——无源区分。
- **新建 UnitBulletEntity 双轨**：否决——一个 collidesStructure 标志足够。
- **跳过 armor**：否决——保底公式一行，留口给未来。

## 后果

- Flare 子弹（#84/#研发票）置 `collidesStructure=true`，其余字段按 ADR-0012 量纲。
- 裂纹/爆点表现复用 #42/#62，零新渲染系统。
- 二期 #34 的其余结构伤害来源（玩家/爆炸）仍留二期，本 ADR 只开敌方子弹一条口。
- 结构 Health 对 client 渐进可见（Jade 侧本期不强制，future）。