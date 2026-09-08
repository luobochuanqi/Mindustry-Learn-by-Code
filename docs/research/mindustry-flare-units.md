# Mindustry flare 单位与行为系统深度调研（面向 #80 规格锁定）

- 调研日期：2026-09-08
- 目标：为 MTurrets 的"飞行单位（flare 原型）+ 结构伤害"两个特性锁定数值与行为规格
- 来源：`ref/mindustry`（上游源码部分镜像，行号已逐条抽验）+ 上游仓库/官方文档（license）+ 大型 mod 工程实践（Create/IE/Alex's Mobs，URL）
- 说明：镜像**不含** archetype 生成物（无 `gen/`、无 `entities/types/Unit.java`）与 `world/Building.java`（Building 逻辑已拆进 `entities/comp/BuildingComp.java`），本文据 comp 源码还原，缺失处在 §2 显式标注

---

## 1. flare 数值全表 + MC 换算

### 1.1 上游 flare 定义（原样，含行号）

`core/src/mindustry/content/UnitTypes.java:1037-1079`

```java
flare = new UnitType("flare"){{
    researchCostMultiplier = 0.5f;      // 1038  科技树成本乘数
    speed = 2.7f;                       // 1039  移动速度（世界单位/tick）
    accel = 0.08f;                      // 1040  加速度（速度占比）
    drag = 0.04f;                       // 1041  空气阻力（速度占比）
    flying = true;                      // 1042  飞行单位
    health = 70;                        // 1043  血量
    engineOffset = 5.75f;               // 1044  引擎偏移（仅渲染）
    targetFlags = {generator, null};    // 1045  建筑目标优先级：发电机 → 兜底最近
    hitSize = 9;                        // 1046  命中框半边长（px）
    itemCapacity = 10;                  // 1047  物品容量
    circleTarget = true;                // 1048  绕目标盘旋（轰炸机式）
    omniMovement = false;               // 1049  不可任意方向移动（只能朝面向方向）
    rotateSpeed = 5f;                   // 1050  机体转向速度（度/tick）
    circleTargetRadius = 60f;           // 1051  盘旋半径（px）
    wreckSoundVolume = 0.7f;            // 1052  坠毁音效音量

    moveSound = Sounds.loopThruster;    // 1054  移动循环音
    moveSoundPitchMin = 0.3f;           // 1055
    moveSoundPitchMax = 1.5f;           // 1056
    moveSoundVolume = 0.2f;             // 1057
}};
```

`UnitType` 默认值中 flare 未覆写、但对其行为生效的关键项（`type/UnitType.java`）：
- `armor = 0f`（88）— flare 无护甲
- `targetAir = true`（165）、`targetGround = true`（167）— 可打空气与地面
- `faceTarget = true`（169）、`circleTarget=false→true 已覆写`（171）、`autoDropBombs = false`（173）
- `omniMovement` 默认 `true`（223）→ flare 显式置 `false`
- `itemCapacity = -1`（378）→ flare 显式 `10`（否则 init 里按 `hitSize*4` 取整，见 §2 `UnitType.java:959-961`）

武器（`UnitTypes.java:1059-1078`，匿名 `Weapon` + `BasicBulletType`）：

| 字段 | 值 | 行 | 说明 |
|---|---|---|---|
| Weapon.y | 1f | 1060 | 枪口沿机体前向偏移（px） |
| Weapon.x | 0f | 1061 | 枪口横向偏移（px） |
| Weapon.minShootVelocity | 2f | 1062 | 机体低于此速度不射击 |
| Weapon.shootCone | 10f | 1063 | 开火准锥半角（度） |
| Weapon.reload | 80f | 1064 | 整轮射击间隔（tick） |
| Weapon.shoot.shots | 3 | 1065 | 每"扳机"3 发 |
| Weapon.shoot.shotDelay | 3f | 1066 | 3 发之间间隔（tick） |
| Weapon.ejectEffect | Fx.casing1 | 1067 | 抛壳特效 |
| Weapon.mirror | false | 1068 | 不镜像（单枪口，非左右双管） |
| Weapon.bullet = BasicBulletType(2.5f, 9) | — | 1069 | 构造 = (speed, damage) |
| bullet.inaccuracy | 4f | 1070 | 每发散布（度） |
| bullet.width / height | 7f / 9f | 1071-1072 | 弹体渲染尺寸（px） |
| bullet.lifetime | 32f | 1073 | 存活（tick） |
| bullet.shootEffect | Fx.shootSmall | 1074 | 枪口特效 |
| bullet.smokeEffect | Fx.shootSmallSmoke | 1075 | 枪口烟 |
| bullet.ammoMultiplier | 2 | 1076 | 每发耗 1/2 弹（弹药经济学） |

**BulletType 默认值**（`entities/bullet/BulletType.java`，flare 弹未覆写即取默认）：`hitSize = 4`（43）、`drag = 0f`（49）、`knockback = 0`（113，`public float knockback;` 默认 0）、`collidesAir/collidesGround = true`（129）、`collidesTiles = true`（125）、`pierceArmor=false`（187）、`armorMultiplier=1`（189）、`buildingDamageMultiplier`（直击建筑伤害乘子，见 §5）。

**有效射程**：`BulletType.range` 在 `init()` 里由 `calculateRange()` 算出（`BulletType.java:402,435-437`）。非 override、无 spawnUnit 时 = `lifetime × speed`：
- flare 弹：`32 × 2.5 = 80px` ≈ `10 blocks`（8px/格）
- 单位 `UnitType.range`：init 时取所有 `useAttackRange` 武器的 `min(weapon.range() − 4f margin)`（`UnitType.java:963-975`）→ flare 单位攻击/逼近射程 ≈ `80 − 4 = 76px`

### 1.2 MC 换算表

**像素→格比例**：Mindustry `tilesize = 8`（`Vars.java:135`，1 tile = 8 世界单位 px）。MTurrets 项目既有约定 **1 Mindustry 格(tile) = 1 MC block**，故 **1 MC block = 8 Mindustry px**，速度类标量换算系数 **×3/8**（项目 #76 子弹已用 `2.5 → 0.9375` 实证，见 `DuoTurretBE.kt:44 speed=0.94f`、`ScatterTurretBE.kt:50`）。**tick 类标量（reload/lifetime/shotDelay/accel 分母）不缩放**——Mindustry 与 MC 同为逻辑 20Hz 主循环，tick 对齐直接搬。

| 统计项 | Mindustry 值 | MC 建议值 | 依据 |
|---|---|---|---|
| 单位移动速度 `speed` | 2.7 px/t | **1.01 block/t** | ×3/8；MC 飞行 mob 典型 0.6–1.0，略快体现"突袭" |
| 加速度 `accel` | 0.08（占 speed 比） | 保留比例，`accel≈0.08×maxSpeed` 每 tick | Mindustry 是"目标速度向量差限幅"式加速（§4），MC 无原生 → 自实现 `moveAt` |
| 阻力 `drag` | 0.04（每 tick 速度×(1−drag·Δ)） | 保留 0.04，自实现 `vel.scl(max(1−drag·Δ,0))` | MC 无 per-entity drag，自实现（§4） |
| 血量 `health` | 70 | **70**（或按 MC 血条 /2 → 35 显示） | 数值直搬；显示层再除 |
| 护甲 `armor` | 0 | 0 | flare 无护甲 |
| 命中框 `hitSize` | 9 px（半边） | **0.9 block（半宽）/ 约 1.8 block 宽** | /8；飞行 mob 碰撞盒 0.6×0.6 偏小，可用 ~0.9 半径的自定义盒 |
| 盘旋半径 `circleTargetRadius` | 60 px | **7.5 block** | /8 |
| 转向速度 `rotateSpeed` | 5 度/t | **5 度/t** | MC 渲染 yaw 就是度，直搬；`Angles.moveToward` 用**度**（§4） |
| `omniMovement` | false | 保留：只能朝面向方向推进 | 影响 `movePref` 分支（§4） |
| 武器射程（弹） | 80 px | **10 block** | lifetime×speed/8 |
| 单位攻击射程 `range` | 76 px | **9.5 block** | (80−4)/8 |
| 弹速 | 2.5 px/t | **0.9375 block/t** | ×3/8（#76 既有先例） |
| 弹 damage | 9 | **9** | 直搬 |
| 弹 lifetime | 32 tick | **32 tick** | tick 直搬 |
| reload（整轮） | 80 tick | **80 tick** | tick 直搬（4s 一轮，偏慢，符合 3 连发点射手感） |
| shots / shotDelay | 3 / 3 tick | **3 / 3 tick** | tick 直搬 |
| shootCone / inaccuracy | 10 / 4 度 | **10 / 4 度** | MC yaw/pitch 为度，直搬 |
| `minShootVelocity` | 2 px/t | **0.75 block/t** | ×3/8（机体低于此速度不射击） |
| 每发弹药 `ammoMultiplier` | 2（每发耗 1/2） | 每发耗 0.5 弹 或 整发 | 经济模型可整发简化 |
| `targetFlags` | {generator, null} | 建筑目标优先级：发电类→最近 | MC 侧映射为 block 分类（§3） |
| `itemCapacity` | 10 | 视特性是否需要搬运；否则 0 | flare 上游可载 10 物品 |

> 显示换算（非核心）：上游 logic 感知 `speed = type.speed·60/tilesize`（`UnitComp.java:297`）、`size = hitSize/tilesize`（:306）、`range/tilesize`（:283）——即 Mindustry 内部"格"感知 = 世界单位 ÷8，与 MC block 天然对齐，进一步佐证 §1.2 的 /8 换算。

---

## 2. 单位类型体系（UnitType / Weapon / BulletType / comp 架构）

### 2.1 UnitType 关键字段语义（`type/UnitType.java`）

- **`flying`**（160-161，默认 false）：飞行单位，elevation 恒 1、走 `layerFlying` 物理层、`wobble`（162）飘移。飞行单位由 `aiController` 默认给 `FlyingAI`。
- **`omniMovement`**（222-223，默认 **true**）：能否任意方向移动；false 时只能朝面向方向推进（`movePref`→`rotateMove`，§4）。**flare=false** 是它"像固定机头战斗机"的关键。
- **`circleTarget`**（170-171，默认 false）：飞行盘旋（轰炸机式）。true 时移动阶段走 `circleAttack(circleTargetRadius)` 而非 `moveTo`。
- **`targetFlags`**（358-359，默认 `{null}`）：建筑目标优先级数组，`null` 表示"取最近"兜底；索敌先按 flag 遍历（§3）。
- **`targetAir`/`targetGround`**（164-167，默认均 true）：能否索敌空气/地面单位。
- **`armor`**（87-88，默认 0）：护甲，伤害经 `Damage.applyArmor` 减伤（§5）。
- **`weapons`**（287-288，`Seq<Weapon>`）：全部武器。
- **AI 装配**（278-281）：
  - `aiController = () -> !flying ? new GroundAI() : new FlyingAI()` — 非玩家单位默认 AI。
  - `controller = u -> !playerControllable || (u.team.isAI() && !u.team.rules().rtsAi) ? aiController.get() : new CommandAI()` — 玩家可控单位包一层 `CommandAI`（它内部再按 `UnitCommand` 分派 `commandController`，CommandAI.java:146-157）。
- **range 初始化**（963-995）：`margin=4f`；`range=min(weapon.range()−margin)`、`maxRange=max(...)`；无武器时退到 `mineRange`。

### 2.2 Weapon 字段（`type/Weapon.java`）

- `x/y`（92-93，默认 x=5,y=0）：枪口相对机体中心偏移（px），经 `Angles.trnsx/y(rotation, x, y)` 旋转到世界（AIController.java:205-206）。
- `reload`（74-75，默认 1）：整轮 tick 数。`shotsPerSec() = shoot.shots·60/reload`（198-199）。
- `shoot`（96-97，`ShootPattern`）：`ShootPattern.shots`（9，每扳机数）、`shotDelay`（13，发间 tick）、`firstShotDelay`（11）。`shoot(totalShots, handler)` 逐发 `handler.shoot(0,0,0, firstShotDelay+shotDelay*i)`（`pattern/ShootPattern.java:22-26`）。
- `shootCone`（108-109，默认 5 度）：开火准锥半角。
- `minShootVelocity`（124-125，默认 -1=不限）/ `maxShootVelocity`（126-127）：机体速度低于/高于阈值不射击。
- `mirror`（39，默认 **true**）：镜像成左右双管；`mirror=true` 时 init 里生成副本并把 reload/recoil 翻倍（`UnitType.java:1046-1050`）。**flare 显式 false → 单枪口**。
- `range()`（294-296）= `bullet.range`。
- `baseRotation`（49）、`rotate`（45，默认 false）、`top`（51）、`controllable`（59）、`aiControllable`（61，默认 true）。

### 2.3 BulletType 相关字段（`entities/bullet/BulletType.java`）

- `speed`（36-37）、`damage`（40-41）、`lifetime`（32-33）、`hitSize`（42-43，默认 4）、`drag`（48-49，默认 0）、`knockback`（112-113，默认 0）、`inaccuracy`（92-93）、`ammoMultiplier`（94-95，默认 2）、`collidesAir/Ground`（128-129，默认 true）、`collidesTiles`（124-125）、`pierceArmor`（186-187）、`armorMultiplier`（188-189）、`blockArmorMultiplier`（190-191）。
- 射程：`range`（161）由 `calculateRange()`（435-437）= `lifetime×speed`（非 override 分支）。

### 2.4 comp 架构（archetype 实体）

Mindustry 实体由 archetype 代码生成器从 `entities/comp/*.java`（`@Component` 注解）生成 `gen/` 下的具体类。**镜像不含生成物**：无 `gen/`、无 `entities/types/Unit.java`（`ls` 验证）。但 comp 源码完整，携带职责如下：

| comp | 携带 | 关键方法/字段 |
|---|---|---|
| `PosComp` | x,y（世界 px） | 位置源 |
| `VelComp` | `vel`（Vec2） | `update()`（:22-34）：`move(vel·Time.delta)` → 逐轴若位置没变则该轴 vel 清零；随后 `vel.scl(max(1−drag·Time.delta,0))`（:32）。`@MethodPriority(-1)` 保证先于其他 comp。客户端不本地积分（除非本地实体）。`move` 走 `collisions.move`（碰撞）或裸加（:65-74） |
| `HitboxComp` | `hitSize`、lastPos/delta | `hitbox(rect)=setCentered(x,y,hitSize,hitSize)`（:65-67）；`hitboxTile`（:69-76，贴地碰撞盒取 `min(hitSize·0.66,7.8)`）；`deltaLen/deltaAngle`（供 `Predict`） |
| `RotComp` | `rotation` | 机体朝向（度） |
| `HealthComp` | `health/maxHealth/dead/hitTime` | `damage`/`kill`/`heal`（:33-117，§5）；`killed()` 由实体覆写 |
| `StatusComp` | 速度/伤害/血量/装填/阻力乘子 + `armorOverride` | 状态效果聚合（:22-24,199-236） |
| `WeaponsComp` | `mounts`（WeaponMount[]） | 每武器一个 mount（`WeaponMount.java:8-59`：weapon, reload, rotation, aimX/aimY, shoot, rotate, target, retarget） |
| `UnitComp` | 机动+武器+感知 | `moveAt/movePref/rotateMove/lookAt`（§4）、`speed()`、`wobble()`、`prefRotation`、`setupWeapons`（:547,910）、logic `sense`（:266-312） |
| `TeamComp`/`OwnerComp`/`Itemsc` | 队伍/拥有者/物品 | — |

`WeaponMount`（`entities/units/WeaponMount.java:8-59`）是"实例化武器"：`target`（当前 AI 目标）、`aimX/aimY`（瞄准世界坐标）、`shoot/rotate`（本 tick 是否射击/转向）、`retarget`（重索敌计数器）。

> **3D MC 移植注意**：comp 的 `collisionLayer`（UnitComp.java:485-488：legs/ground/flying 三层）依赖 2D 物理层；MC 移植需自行决定用 MC 的 `MobType` + 碰撞盒代替，飞行单位走"无重力/可悬停"（`noGravity=true` + 自定义 `travel`）。

---

## 3. AI 行为逻辑（Kotlin 可 1:1 移植的控制流）

### 3.1 每 tick 主循环（`AIController.updateUnit` :44-56）

```
updateUnit():
  if (useFallback() && fallback 可用) { fallback.unit(unit); fallback.updateUnit(); return }
  updateVisuals()      // :102-108  飞行: wobble() + lookAt(prefRotation())
  updateTargeting()    // :114-118  hasWeapons() → updateWeapons()
  updateMovement()     // :110-112  空桩，子类覆写（FlyingAI/CommandAI）
```

`CommandAI.updateUnit`（:116-171）额外先处理命令/stance，再按 `command` 分派 `commandController`（无命令时走 `defaultBehavior()` :198）。**玩家单位 = `CommandAI` 外壳；纯 AI 单位 = 直接 `FlyingAI`**（装配见 UnitType.java:279-281）。

### 3.2 索敌：updateWeapons（`AIController.updateWeapons` :172-256）

```
rotation = unit.rotation - 90            // :173  mount 偏移基准角（机体朝向 −90°，因贴图朝向偏移）
ret = retarget()                          // :174  重索敌节拍
if (ret) target = findMainTarget(unit.x, unit.y, unit.range(), targetAir, targetGround)
noTargetTime += Time.delta                // :180
if (invalid(target)) { 若 target 是 Healthc 且 !isValid → targetInvalidated(); target=null }
else noTargetTime = 0
unit.isShooting = false
for (mount : unit.mounts):
  weapon = mount.weapon; wrange = weapon.range()
  if (!weapon.controllable || weapon.noAttack) continue         // :198
  if (!weapon.aiControllable) { mount.rotate=false; continue }  // :200-203
  mountX = unit.x + Angles.trnsx(rotation, weapon.x, weapon.y)  // :205-206  枪口世界坐标
  mountY = unit.y + Angles.trnsy(rotation, weapon.x, weapon.y)
  if (singleTarget) mount.target = target                        // :208-209  单目标单位所有 mount 共享
  else {
    if (ret) mount.target = findTarget(mountX, mountY, wrange, bullet.collidesAir, bullet.collidesGround)  // :211-212
    if (checkTarget(mount.target, mountX, mountY, wrange)) mount.target = null   // :215-217
  }
  shoot = false
  if (mount.target != null):
    shoot = mount.target.within(mountX, mountY, wrange + target.hitSize/2) && shouldShoot()   // :222-223
    if (autoDropBombs && !shoot) { …轰炸机自投弹逻辑 :225-230 }
    (to = Predict.intercept(unit, mount.target, weapon.bullet)); mount.aimX=to.x; mount.aimY=to.y   // :232-234  提前量拦截
  mount.shoot = mount.rotate = shoot     // :237
  if (!shouldFire()) mount.shoot = false
  unit.isShooting |= mount.shoot
  // 无目标且久停：mount 转回 baseRotation（:244-249）
  if (shoot) { unit.aimX=mount.aimX; unit.aimY=mount.aimY }
```

**重索敌节拍**（`retarget()` :285-287）：`timer.get(timerTarget, target==null ? 40 : 90)` — 无目标每 40 tick 重索，有目标每 90 tick。`timerTarget` 初始随机 40 tick（:40）。目标被 `invalidateTarget` 判无效时 `targetInvalidated()` 把该 timer 置 -1 → 下 tick 立即重索（:167-170）。

**"单位优先、建筑其次"** 由 `target()` → `Units.closestTarget`（Units.java:272-281）实现：先 `closestEnemy`（单位，:275），无单位才 `closestTarget`（建筑/tile）。`invalidateTarget`（:151-158）判：超程 / 同队 / 不可命中。

### 3.3 飞行主目标选择：FlyingAI（`ai/types/FlyingAI.java`）

`findMainTarget(x,y,range,air,ground)`（:41-73）——**core 优先**：

```
core = targetFlag(x, y, BlockFlag.core, true)          // :43  最近敌方 core
if (core != null && within(x,y,core, range)) return core  // :45-47  core 在射程内 → 直接打 core
if (state.rules.randomWaveAI):                          // :49  无波次时按随机 flag
    rand.seed(unit.type.id + (waves ? state.wave : unit.id))
    5 次尝试: targetFlagActive(x,y, randomTargets[rand], true) → 命中即返回
    兜底: target(x,y,range,air,ground) → 最近
else:                                                    // :60  按 unit.type.targetFlags 顺序
    for (flag : unit.type.targetFlags):                 //  flare = {generator, null}
        if (flag == null): result = target(x,y,range,air,ground)  → 有则返回   // 兜底最近
        else if (ground): result = targetFlagActive(x,y,flag,true) → 有则返回   // 发电机
return core                                              // :72  最终兜底仍是 core（哪怕超程）
```

- `findTarget`（:33-39）：取 `findMainTarget`；若主目标在射程内就用它，否则退回 `target()`（最近）。
- **`targetFlagActive`**（AIController.java:276-279）：`Geometry.findClosest(x,y, 敌方带该 flag 的建筑集, t -> (t.items.any() || t.status()!=noInput) && t.block.targetable)` — 要求建筑**有活动物品/状态**且 `targetable`。
- `target()`（:281-283）：单位 `checkTarget(air,ground)` + 建筑过滤 `ground && (targetUnderBlocks || !block.underBullets)`。
- **MC 移植要点**：把 `BlockFlag` 映射为 MC block 分类（如发电类→`BlockTag`），"core"映射为敌方基地核心方块；"有活动物品/状态"映射为 BE 是否有存货/在运行。

### 3.4 移动：FlyingAI.updateMovement（:15-31）

```
unloadPayloads()
if (target != null && unit.hasWeapons()):
    if (unit.type.circleTarget) circleAttack(unit.type.circleTargetRadius)   // flare → 盘旋
    else { moveTo(target, unit.type.range * 0.8f); unit.lookAt(target) }     // 非盘旋：逼近到 0.8×射程
if (target == null && state.rules.waves && unit.team == defaultTeam):
    moveTo(getClosestSpawner(), dropZoneRadius + 130f)
```

**circleAttack**（AIController.java:318-351）：
```
vec = target - unit; ang = unit.angleTo(target); diff = angleDist(ang, unit.rotation)
if (target 是 Unit && 同 collisionLayer):          // 打的是同层单位（近距缠斗）
    avoidDist = target.physicSize() + 30f          // :327  同层单位反向规则半径
    if (turningAway): vec.setLength(prefSpeed()).scl(-1f); unit.movePref(vec);  // :330-331  掉头拉开
        if (!within(target, circleTargetRadius*0.5 + target.physicSize())) turningAway=false
        return
    else if (within(target, avoidDist)): turningAway = true   // :337-338  进入危险半径 → 开始拉开
if (diff > 70f && vec.len() < circleLength): vec.setAngle(unit.vel().angle())  // :342-343  角度差>70° 且过近 → 保持当前航向
else if (omniMovement): vec.setAngle(moveToward(unit.vel().angle(), vec.angle(), 6f))  // :344-345  每 tick 最多转向 6°
vec.setLength(prefSpeed()); unit.movePref(vec)
```

**moveTo**（AIController.java:383-432，非盘旋分支用）：`speed=prefSpeed()`；`length = clamp((dst-circleLength)/smooth, -1, 1)`（smooth 默认 100，:371-372）；`vec.setLength(speed·length)`；`length<-0.5` → 悬停(`setZero`)或拉远(`rotate(180)`, keepDistance)；`length<0` → 停；`!omniMovement && rotateMoveFirst` 时先 `lookAt` 且角差<3° 才 `movePref`，否则直接 `movePref`。`prefSpeed()=unit.speed()`（:434-436）。

**3.5 控制器目标获取 vs 武器射程**：单位级 `target` 用 `unit.range()`（≈76px，:177/223，`within` 再叠 `target.hitSize/2`）；每 mount 用 `weapon.range()`（=80px）独立判定（:195/212/223）。瞄准用**提前量拦截** `Predict.intercept(unit, mount.target, weapon.bullet)`（:232；`entities/Predict.java` 二次方程，速度源 `dst.deltaX()/deltaY()` 来自 HitboxComp lastPos）→ MC 移植用目标 mob 的 `getDeltaMovement()` 做同式。

**时间源**：全部 `Time.delta`（默认 1）。MC 侧 = 每 server tick 一次（`tick()` 内），无需可变 Δ；要平滑再引 `partialTick`（#76 已有子弹先例）。

---

## 4. 运动学原式（变量名对齐上游，rad vs deg 标注）

`Time.delta` 下文记 Δ（通常 =1/tick）。**角度单位**：Mindustry/libgdx `Angles` 与 `Vec2.angle()/rotate()/setAngle()` 一律**度（0–360）**；`rotation` 字段也是度。MC 的 yaw/pitch 同为度 → **直搬，无需换算**。只有 `Vec2` 内部 sin/cos 用 rad（libgdx 内部 `MathUtils.radians`），移植到 MC 用 `Mth.toRadians`。

### 4.1 机动（`entities/comp/UnitComp.java`）

```
movePref(v): omniMovement ? moveAt(v) : rotateMove(v)          // :132-138
moveAt(v): moveAt(v, type.accel)                                // :140-142
moveAt(v, accel):                                              // :101-105  目标向量加速
    t = v
    delta = (t - vel).limit(accel * |v| * Δ)   // 向目标速度向量靠拢，步长上限 = accel·|v|·Δ
    vel += delta
rotateMove(v):                                                 // :148-154  非全向：先转后走
    moveAt(trns(rotation, |v|), type.accel)   // 沿当前朝向推进（用机体朝向 rotation，非 v 方向）
    if (!v.isZero()) rotation = moveToward(rotation, v.angle(), type.rotateSpeed * Δ * speedMultiplier)
lookAt(angle): rotation = moveToward(rotation, angle, type.rotateSpeed * Δ * speedMultiplier)   // :490-492
```
`Angles.moveToward(from,to,maxDelta)`（libgdx）：最短角路径，每步最多转 `maxDelta` 度，返回朝 to 逼近后的角。**这是转向限速的唯一来源**——无弹簧、无加减速曲线，纯角度钳制。

### 4.2 速度积分（`entities/comp/VelComp.java:22-34`）

```
@MethodPriority(-1) update():            // 先于其他 comp
  px,py = x,y
  move(vel.x·Δ, vel.y·Δ)                 // 位移（内部走碰撞）
  if (x==px) vel.x=0                     // 撞墙贴零
  if (y==py) vel.y=0
  vel.scl(max(1 - drag·Δ, 0))            // :32  阻力衰减（指数）
```
阻力是**每 tick 乘法衰减** `vel *= (1−drag·Δ)`，非 MC 的固定空气摩擦。MC 移植需自实现（`setDeltaMovement(vel.multiply(1-drag))`）。

### 4.3 机动数学小结（MC 3D 决策点）

- 位置 x,y 是 2D 世界 px；MC 为 3D（z 高度）。飞行单位建议 `noGravity`、自管 `travel()` 每 tick 执行 §4.1/4.2，高度由 `elevation` 逻辑或固定悬停高度决定。
- `collisionLayer`（legs/ground/flying）依赖 2D 物理层 → MC 用碰撞盒 + `MobType` 替代；`within(x,y,r)` 用 2D 水平距离（MC `distanceToSqr` 去掉 y 分量）。
- `tileOn()/canPass(tileX,tileY)`（VelComp:47-55）依赖 2D tile 网格 → MC 飞行单位无地形阻挡，可整段删除；仅地面单位需 `level.noCollision` 检查。

---

## 5. 结构伤害语义（Building 模型 → MC 结构血量）

### 5.1 建筑血量与护甲

- `Building.health`/`maxHealth` 由 `block.health` 初始化（`entities/comp/BuildingComp.java:146-148`），读档时 `health = min(读值, block.health)` 防数值漂移（:222）。
- `Block.armor = 0f` 默认（`world/Block.java:191-192`）。
- 减伤公式 `Damage.applyArmor(damage, armor)`（`entities/Damage.java:659-661`）：
  ```
  result = max(damage - armor, minArmorDamage · damage)   // 减伤但不低于 minArmorDamage 比例
  ```
  **不是** `max(damage-armor, 0)`，有保底系数 `minArmorDamage`（Vars 常量）。MC 移植结构血量可简化为 `max(damage-armor, 0)`，或保留保底比例。
- `Block.targetable = true` 默认（`world/Block.java:302-303`）— 是否被单位索敌。`Building.targetable` 还参与 `UnitType.targetable(unit,targeter)`（UnitType.java:635-637）。

### 5.2 子弹命中建筑（直击）

`BuildingComp.collision(Bullet other)`（:1760-1778）：
```
wasDead = health <= 0
t = other.type
damage = t.buildingDamage(other)                 // = other.damage · buildingDamageMultiplier
if (!t.pierceArmor)
    damage = applyArmor(damage, block.armor · t.armorMultiplier · t.blockArmorMultiplier)  // :1767-1769
damage(other, other.team, damage)                // → damage(float) 扣血
if (health<=0 && !wasDead) fire(BuildingBulletDestroyEvent(self(), other))   // :1773-1774
return true                                        // 子弹被消耗
```
`handleDamage(amount)` 基类恒等（:1744-1746），多层建筑/护盾类可覆写做减伤。**点伤害无 hitSize 缩放**——`buildingDamage` 是 `damage×buildingDamageMultiplier`，与命中点无关；面积/溅射走 §5.4 的 `Damage.damage`（半径线性衰减，见下）。

### 5.3 建筑被摧毁（killed → onDestroyed）

`BuildingComp.killed()`（:2248-2255）：
```
dead = true
fire(BlockDestroyEvent(tile))
onDestroyed()                 // :1491-1540
tile.remove()                 // 清除 tile
```
`onDestroyed()`（:1491-1540）**不掉落物品**——它只做**爆炸**：按内容物（items/liquids/power）计算 `explosiveness/flammability/power`，调 `Damage.dynamicExplosion(...)`（:1519）产生火焰/爆炸/冲击，并 `Effect.rubble`（:1521-1522，`createRubble` 时）。**建筑本身不 drop 方块/物品**（拆除走 `onDeconstructed` 的液体落地 :1482-1487，战斗摧毁只爆炸）。
> **MC 移植要点（对齐 #79 结构范围）**：结构 = 锚点 + 成员格；**血量挂锚点 BE**。摧毁 = `onDestroyed` 语义 → **整结构拆除、内容不散落**（或仅按锚点 BE 逻辑决定掉落），播一次爆炸/破碎粒子 + 音效，然后 `tile.remove` 全部成员格。Mindustry 的"建筑不掉落、只爆炸"正对应 #79 想要的"full teardown w/o contents scatter"。

### 5.4 面积/溅射 vs 直击

`Damage.damage(team,x,y,radius,damage,complete,air,ground,scaled,source,armorMult)`（`entities/Damage.java:505-518`）：半径内敌方单位 `calculateDamage(dst,radius,damage)`（距离线性衰减）扣血，建筑同半径按 `buildingDamage` 扣；`scaled` 时命中半径 = `radius+hitSize/2`（:507）。**直击** = `collision(Bullet)` 单点；**面积** = 此半径遍历。flare 弹无 `splashDamageRadius` → 纯直击。

> 单位血量对比（供 flare 血条）：单位走 `HealthComp`（:33-117）+ `ShieldComp.damage`（:28-31）`= applyArmor(amount,armor)/healthMultiplier/state.rules.unitHealth(team)`；`kill()`（:33-40）`health≤0 → dead; killed(); remove()`，`killed()` 由生成实体覆写（镜像缺失），通用死亡效果见 `Units.unitSafeDeath`（:104-112）。

---

## 6. 大型 mod 实体工程案例（面向 Mindustry 式飞行单位移植）

> 说明：本仓 `ref/create` 镜像已被清空（0 文件），故不引 Create 本地行号；`docs/research/create-engineering-patterns.md`（2026-08-28，基于 Create 6.0.11 commit 0924e93）已含 Create 工程范式，本文只补**实体**视角并核对上游仓库真实路径。

**Create**（`github.com/Creators-of-Create/Create`，NeoForge，分支 `mc1.21.1/dev`）：
- 自定义移动实体 = **直接继承 `Entity`（非 `Mob`）**：`content/contraptions/AbstractContraptionEntity.java:79 extends Entity implements IEntityWithComplexSpawn`，`tick()`（:368-388）分端。不走 goal selector（无寻路需求），运动由服务端 contraption 状态机驱动。
- 同步：`defineSynchedData`（:598）+ `writeAdditional(compound, registries, spawnPacket)` 双通道（:606 生成包 / :613 存档），`IEntityWithComplexSpawn` 走 `readAdditional`（:627-632）。即 **entity data（少量标量）+ 自定义 spawn 包（大块结构）分流**——与 Mindustry 的 `@SyncLocal vel`（VelComp:16）/`UnitSyncContainer` 思路一致。
- 客户端插值：Create 靠 contraption 状态机 + 客户端读 synced 状态；炮塔角度插值范式见本仓 create-engineering-patterns.md §2.5（`Mth.lerp(partialTick,...)` + 速度外推 + 差值衰减），可复用到飞行单位 yaw。
- 渲染：contraption 用 `EntityRenderer` + 每 contraption 动态 model（`ModelPart` 式）；飞行单位同理——服务端权威位置 + 客户端 `partialTick` 插值 + `EntityRenderer`。

**Immersive Engineering**（`github.com/BluSunrize/ImmersiveEngineering`，分支 `1.21.1`）：
- 大量逻辑在 **BlockEntity**（multiblock 主从，`api/multiblocks/blocks/registry/MultiblockBlockEntityMaster.java`），自定义 `Entity` 仅用于"移动中的机器部件"（如锯片 `sawblade_entity`，见 datagen `EntityTypeTags.java` / `loot/EntityLoot.java`）。
- 启示：MTurrets 炮塔/结构 = BE（已有）；**飞行单位 = 真 `Mob`/`Entity` 子类**，二者不混——结构伤害走 BE（§5），单位走 Mob。

**Alex's Mobs**（`github.com/AlexModGuy/AlexsMobs`，分支 `1.20`）：
- 典型 `Mob` 子类 + **goal selector**（`common/entity/...` 各 mob 带 `AiGoal` 集）+ `EntityRenderer` + `ModelPart` 骨骼 rig + **spawn egg 注册**（`DeferredSpawnEggItem` / `DeferredRegister`）。
- 启示：若要 flare 走 goal selector 体系（`PathNavigate`、`LookAtTargetGoal` 等原版 AI），参考 Alex's Mobs 的 goal 组合 + 渲染/模型/蛋注册；若要 Mindustry 式"手写 controller 状态机"（本调研 §3/§4），则**不用 goal selector**，直接覆写 `serverAiStep()`/`travel()`，运动学用 §4 原式。

**通用范式（三例共通，移植 Mindustry 飞行单位最相关）**：
1. 实体基类：飞行单位 → `Mob`（需目标/血量/AI 生命周期）或 `Entity`（纯自驱，Create contraption 路线）。Mindustry 式建议 **`Mob` + 自驱 `serverAiStep`**（弃用 goal selector，复刻 AIController 状态机）。
2. 服务端权威运动 + 客户端插值：服务端 `tick()` 唯一积分位置（§4），客户端只读 + `partialTick` 插值 + 朝向外推（create 文档 §2.5 范式）。
3. 同步：**entity data**（少量标量：朝向、血量、目标 id）+ **自定义 spawn/update 包**（大块状态），分流（Create `writeAdditional(...,spawnPacket)` / NeoForge `docs.neoforged.net/docs/entities/data/`）。
4. 渲染：`EntityRenderer` + `ModelPart` 骨骼（Alex's Mobs/Create 飞行部件），飞行单位悬停 bob = Mindustry `wobble()`（UnitComp:96-99）的 MC 等价。
5. 注册：`DeferredRegister.Entities` + `EntityType.Builder.of(factory, MobCategory.MISC)` + spawn egg（`DeferredSpawnEggItem`），见 `docs.neoforged.net/docs/entities/`。

---

## 7. License 核实

**Mindustry 上游**（`github.com/Anuken/Mindustry`）：
- **代码 = GPLv3**。上游仓库 `LICENSE` 文件为 GNU GENERAL PUBLIC LICENSE v3（经 GitHub API 确认 `spdx_id: GPL-3.0`，`https://api.github.com/repos/Anuken/Mindustry/license`）。
- **美术/资产**：上游 `LICENSE` 仅声明代码 GPLv3；本地 wiki 镜像（`ref/mindustry/wiki/.../faq.md`、modding 文档）**未包含**明确的"资产 = CC BY-NC-SA 4.0"条文（多次检索 faq/modding/spriting 均无）。`Anuken/Mindustry.wiki` 仓库不存在（`has_wiki:false`，wiki 由 `MindustryGame/wiki` 托管，其 FAQ 亦无资产授权段）。**结论：资产无官方明文授权，按"默示受版权保护、不可自由复制"对待最稳妥**。

**MTurrets** = GPLv3（`LICENSE` 为 GNU GPL v3，已验证）。

**兼容性结论**：
- **允许**（GPLv3 → GPLv3）：MTurrets 作为 GPLv3 项目，**从 Mindustry GPLv3 源码复制/移植逻辑与数值是合法的**（GPLv3 允许派生与复用，产出同为 GPLv3，与 MTurrets 现状一致）。本特性 §1-§5 的数值表 + 控制流 + 运动学 + 伤害语义全部走此路径——**只取"思想/数值/算法"，不复制美术资源**，即使不逐行照抄也完全在许可范围内。
- **不允许**：把 Mindustry **美术/音频/贴图资产**复制进 MTurrets（或其他项目）。资产无 GPL 授权、无 CC 授权，复制即构成对 Anuken 版权的侵犯；且非 GPL 的 CC-BY-NC 类资产混入 GPLv3 发行会产生许可证冲突。**MTurrets 必须使用自制（或明确兼容授权）资产**——本特性 flare 模型/贴图走自制路线（参考 `docs/research/aigc-mc-model-assets.md`）。
- 数值/公式（如 `vel.scl(1-drag·Δ)`、`applyArmor`）属思想表达，不受版权约束；**逐字复制 GPLv3 代码片段**合法（产出 GPLv3），但建议在文档/注释注明"参考 Mindustry (GPLv3)"。

**引用**：
- 代码 license：https://api.github.com/repos/Anuken/Mindustry/license （`GPL-3.0`）
- 上游仓库：https://github.com/Anuken/Mindustry
- NeoForge 实体/数据同步文档：https://docs.neoforged.net/docs/entities/ , https://docs.neoforged.net/docs/entities/data/
- Create：https://github.com/Creators-of-Create/Create ；IE：https://github.com/BluSunrize/ImmersiveEngineering ；Alex's Mobs：https://github.com/AlexModGuy/AlexsMobs
