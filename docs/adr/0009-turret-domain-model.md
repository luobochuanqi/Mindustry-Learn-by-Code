# ADR-0009: 炮台域模型——单层 TurretBE、单位账弹仓、点射扳机扣账

状态：accepted（2026-08-29 敲定，#28）

新范式炮台 = 单层 `TurretBE`（继承 ADR-0003 锚点 BE）承载索敌/旋转/装填/开火管线、Magazine、Coolant 内罐与 Health，弹种差异全部进数据表（TurretSpec Kotlin 代码表 + BulletType，分工依 ADR-0006）。Mindustry 的四层继承链（BaseTurret←ReloadTurret←Turret←ItemTurret）是其 Java 数据驱动注册的副产品，一期 Duo/Scatter 都是物品弹，照搬等于为不存在的复用矩阵交税；ADR-0004 的族内复用一个类即满足。每 tick 时序对位 Mindustry：目标校验 → 装填累加（封顶 reload；尾弹种 reloadMultiplier；Coolant 生效则 ×1.5）→ 每 7 tick 索敌 → 旋转逼近目标角 → 开火门（装填满 ∧ 入 shootCone）→ 扳机扣账 + 按 `(shots, shotDelay)` 排程出膛；装填与瞄准解耦。

关键语义：**Magazine 是单位账**（物品×ammoMultiplier 折算入仓、cap 按单位计），不存物理物品；选弹**后入为主（LIFO）**；点射共享同一次扣账（Scatter 一扳机 2 发扣 1 单位，对位 consumeAmmoOnce）。装弹/退料走**手持右键**：右键结构任一格（Member 代理回锚点）整堆折算入仓、超 cap 部分折算、手持堆按接受量 shrink（#46 取代整堆拒收的 #31 决议，2026-08 打磨阶段定案）；拆除或毁坏时按 `floor(单位/multiplier)` 折回物品散落。阵营过滤 = **只打 Monster**（MC 无队伍系统，避免误伤建造中的自己人）。同步遵 ADR-0005：只发目标 yaw/pitch（低频 update tag）+ 单调开火计数器，瞬时角不上网；后坐值 curRecoil 是 Flywheel 枪管动画的唯一逻辑量。结构 Health 取 Mindustry 原值 1:1（Duo 250、Scatter 200），玩家挖掘走拆除不走伤害。量纲换算规则（全炮台通用）：时长 ×⅓、距离 ÷8、弹速 ×⅜、角速度 ×3。

## Considered Options

- **照搬四层继承链**：否决——一期每层只有唯一子类。
- **固定弹种优先级表**：否决——LIFO 两行实现、原版语义，且无 GUI 下优先级表无处可查。
- **逐发扣账**（点射每颗子弹各扣 1 单位）：否决——与 #26 DPS 心算口径冲突，弹药成本翻倍。
- **Coolant 连续流公式**（换算成 mB/tick 每刻抽罐）：否决——桶灌场景没有连续流，不射击也白白流光；液网期接管道流冷却时再升级连续式（代码留 `ponytail:` 注记）。
- 旧框架的 warmup/minWarmup、heat、喂料槽+autoReload、switchToNextAmmo、TurretConfig Builder+七预设、EffectType 枚举均**不迁入新骨架**：蓄力/热管理是能量武器专属；喂料通道无调用方且折算不乘 ammoMultiplier（与 #25 相悖的带 bug 死路）；切弹按钮无 GUI 无按键；预设工厂是为假想炮台写的投机配置；EffectType 到 MC 粒子端是假接口。legacy 文件本体保留、#39 标注，不删不迁。【2026-08-31 更新】一期收尾拍板全部清理：legacy 框架与 Arc/Meltdown 本体一并删除，换代时按本 ADR 原则在 TurretBE 上重建，不再有"保留运行"的过渡态。

## 后果

- Arc/Meltdown 换代时在同一 TurretBE 上接入瞬时弹道（ADR-0006 射线判定零实体），warmup/charge 与耗电接入点（PowerTurret 语义）届时一并重建，不提前预留字段。
- 本决策只覆盖炮台的装弹/退料语义；机器 Buffer（窑炉/钻头）产物如何取出仍属 #33/#35 执行期决策，不视为已定。
- Duo/Scatter 完整数值表见 #28 决议 comment；数值过强按 ADR-0006 分工在代码表调，不改本 ADR 的形状。
