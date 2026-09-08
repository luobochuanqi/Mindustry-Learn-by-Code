# ADR-0012: 单位实体架构——UnitMob 基类、自驱 steering、目标角同步

状态：accepted（2026-09-08，#81 定稿）

为 MTurrets 引入**单位**（Unit）：自主移动的敌对飞行实体，首个实现 **Flare（星辉）**，数值/行为复刻 Mindustry（`docs/research/mindustry-flare-units.md` 为规格来源）。本 ADR 定单位侧实体架构；结构伤害另行 ADR-0013（#82）。

## 类族形状

新抽象基类 **`UnitMob : Mob`** 收敛 Mindustry comp 行为（对齐 Mindustry archetype 组合语义，但用单继承表达）：
- **运动学原式**（research §4）：`moveAt`（目标速度向量差限幅 `accel·|v|·Δ`）、`vel.scl(max(1−drag·Δ,0))` 指数阻力、`rotateMove/lookAt` = `Angles.moveToward` 角度钳制。
- **武器 mount 列表**：`List<WeaponMount>`（Mindustry mount 直译：weapon 引用 + reload/aim 状态 + shoot 标志），`updateWeapons` 每 tick 驱动；Flare 单武器单 mount，但结构为多 mount 预留。
- **通用 AI 控制流**（research §3）：重索敌节拍（无目标 40 / 有目标 90 tick、失效置 -1 立即重索）、`circleAttack` vs `moveTo` 分支、`Predict.intercept` 提前量。AI 差异进数据（子类只覆写 `targetFlags` + 盘旋参数）。
- Flare 只提供 **`UnitSpec` Kotlin 代码表**（同 TurretSpec 先例）+ 极少覆写。JSON 数据驱动留到多单位/需热改时。

单位注册走 `ModEntities.MOD_ENTITIES`（同 TURRET_BULLET），刷怪蛋 + `/summon` 入场（规划期定案）。

## 逻辑 tick 布局

`MuMob` 用 `customServerAiStep()` 跑全部 steering（运动积分、索敌、武器更新）——服务端每 tick 跑、天然不进客户端 tick（Wither/Dragon/Shulker 同款 vanilla 缝）。`travel()` 仅服务端由自设 deltaMovement 走碰撞。

## 高度/3D 平面化

固定悬停高度（地面 +2.5 block）+ 轻微 bob（Mindustry wobble 等价）；水平运动 2D 平面化（`within` 用水平距离，research §4.3）。高度不参与索敌距离。

## 同步契约（守 ADR-0005：不发瞬时角）

- 位置：vanilla 每 tick 同步（`updateInterval=1`，同 #76 子弹先例）。
- 角度：synced data 只发 **目标角**（单位级 targetYaw）+ 运动节流标量（0..1，由目标速度/引擎状态推）+ 开火脉冲计数器。客户端 Renderer 每 partialTick 用 `rotateSpeed×partialTick` 把当前渲染角逼近目标角（同 DuoVisual smoothYaw 积分、#76 partialTick）。
- 瞬时角不上网；目标角是低频低标量，客户端自己积分出平滑朝向。

## 渲染

`EntityRenderer` + `ModelPart` 骨骼 rig（research §6 范式：Create/IE 实体经 Renderer+ModelPart，Alex's Mobs 同款）。flare.bbmodel → 运行时模型由 #83 资产票产出，挂到 renderer。renderer 读 synced 目标角做 partialTick 平滑 + wobble bob。

悬停 bob = 垂直正弦（引擎光点随 `spec.engineOffset` 挂）；速度节流标量驱动引擎光点亮度/倾角；尾焰粒子走现有 #62 billboard 基建（服务端位置 + 客户端 ticker）。

## 死亡/掉落/归属

Flare 实现 vanilla `Enemy` 标记 → 炮台杀伤走既有 `playerAttack` 归属（击杀者经验，沿用 #60 loaderId 骨架）。Flare **不掉物**（Mindustry 单位击杀不掉落建筑，research §5.3）。死亡表现 = 标准 Mob 死亡。

## 量纲换算修正（#80 错误纠正，本 ADR 权威值）

research §1.2 声称"tick 类标量不缩放（Mindustry 与 MC 同为 20Hz）"——**错误**：Mindustry 是 **60Hz**（`mindustry-bullet-fx.md:7`：每 tick = 1/60s）。正确采用 ADR-0009 量纲换算律：**时长 ×⅓、距离 ÷8、弹速 ×⅜、角速度 ×3**。correction 后权威值：

| 项 | Mindustry | MC 正确值 | 依据 |
|---|---|---|---|
| reload | 80 tick | **×⅓ ≈ 27** | 时长 ×⅓ |
| lifetime | 32 tick | **×⅓ ≈ 11** | 时长 ×⅓（→弹程 0.9375×11 ≈ 10 block ✓ 自洽） |
| shotDelay | 3 tick | **×⅓ = 1** | 时长 ×⅓ |
| rotateSpeed | 5°/t | **×3 = 15°/MC t** | 角速度 ×3 |
| fire-rate | 3 shots | 3（点射不变） | 数值直搬 |
| speed | 2.7 | **1.0125 ≈ 1.0** block/t | ×⅜ |
| bullet speed | 2.5 | **0.9375** block/t | ×⅜（#76 已实证） |
| hitSize | 9 px | **÷8 = 1.125 block** 半宽/≈2.25 直径 | 距离 ÷8 |
| circleTargetRadius | 60 | **÷8 = 7.5 block** | 距离 ÷8 |
| weapon range | 80 px | **10 block** | ÷8 |
| unit range | 76 px | **9.5 block** | ÷8 |
| minShootVelocity | 2 px/t | **×⅜ ≈ 0.75** block/t | ×⅜ |

> research 文件保留原文（它标注"未最终换算"），本 ADR 是所有实现/设计票的量纲权威。速度换算碰巧对（×⅜ 已含 60→20 的 ×3）→ 不要据此把"tick 不缩放"当成立结论。

## 领域词表

**Unit**：仿 Mindustry 的自主移动敌对实体（_Avoid_: 单位, 敌人）— 写进 CONTEXT.md。
**Flare**：首个飞行 Unit，星辉（英文原词保留）— 写进 CONTEXT.md。

## Considered Options

- **单类 FlareMob 先走通**：否决——用户明示"一开始做好设计逼近 Mindustry"，且运动学/AI 原式本身是通用逻辑。
- **覆写 tick() 做双端分支**：否决——需手写双端门控，有客户端也积分的风险。
- **直接同步渲染角**：否决——违背 ADR-0005，会来回抖。
- **给 flare 设计掉落**：否决——偏离 Mindustry、无本期消费方。
- **复用 Flywheel 渲染实体**：否决——不适用实体，且 #83 是独立资产路径。

## 后果

- Flare 实现落在下一执行票（#81 只定架构，不落码）。
- #83 资产票产出 flare.bbmodel →运行时 ModelPart rig，接口契约 = 本 ADR 的 Renderer 读目标角 + 节流 + bob。
- 单位侧 GameTest 复用现有单套件（`GameTests.kt`，spawnWithNoFreeWill summon），批次隔离沿用 #50/#55 约定。
- 多武器/多 mount、JSON 数据驱动、玩家可控（CommandAI）均不本期做，结构已预留。