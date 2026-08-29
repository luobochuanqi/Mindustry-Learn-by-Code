# Mindustry 炮台与子弹机制事实文档

> 用途：供 #28（炮台域模型设计）与 #31（Bullet 实体与 Duo 重建）直接引用。
> 范围：只记录 Mindustry 机制事实，不含 MTurrets 设计决策。
> 一手来源：`ref/mindustry`（稀疏检出，commit `dc32943612553832348d95f3ba5b7fe9b00d5099`，issue 所称 dc32943）。所有结论带 `(ref/mindustry/...:行)` 坐标（相对仓库根）。
> 校对规则：wiki 与源码冲突以源码为准；本次 wiki（mindustry.fandom.com）不可达（连接失败），全部结论来自源码，未做 wiki 交叉对照。
> 时间语义：Mindustry 帧率 60 tick/s，文档中"每 tick"= 1/60 秒；所有 reload/寿命/interval 字段单位都是 tick。

## 给 #28 设计者的要点提要

1. 发射管线是「装填门 → 射击门 → 扣弹 → 造弹」，装填与瞄准完全解耦：`handleReload()` 任何情况下都累积 reloadCounter，只有 `reloadCounter >= reload` 且 `shootCone` 内才开火（Turret.java:721-728, 739-748, 601-604）。
2. **子弹在炮台坐标系折算成"弹药单位"（Ammo）**：入仓物品 × `ammoMultiplier` 折成单位，每发扣 `ammoPerShot` 单位；`maxAmmo` 是单位上限不是物品数（ItemTurret.java:155-189, Turret.java:47-49）。
3. 弹种切换规则是**后入为主（LIFO）**：`handleItem` 把刚入仓的弹种条目挪到队尾，`peekAmmo()` 只取队尾；不足一发时 `hasAmmo()` 会与足量条目换位（ItemTurret.java:171-183, Turret.java:695-715）。
4. 弹种数据在发射点以**实例字段拼进 Bullet**：`range()/minRange()/trackingRange()` 都叠加当前弹种的 `rangeChange/minRangeChange`，`reloadMultiplier` 乘进装填速率，`inaccuracy/velocityRnd/lifeRnd` 在 `bullet()` 里消费，`ammoMultiplier` 只在入仓/扣账（Turret.java:340-357, 730-737, 784-825）。
5. 冷却液是**可选的 reload 增速器**：基础装填 `reloadCounter += delta·ammoReloadMultiplier·efficiency`，冷却液在其上再叠加 `amount·efficiency·edelta·heatCapacity·coolantMultiplier·ammoReloadMultiplier`（ReloadTurret.java:28-39, 730-732）；冷却液耗尽只掉速不开不了火（BaseTurret.java:70-78 标为 booster+optional）。
6. 伤害路径分两层：直击走 `BulletComp.collision/tileRaycast → hitEntity/hitTile`（碰撞期扣血+10% 护甲保底），溅射走 `Damage.damage`（半径内独立结算，40%~100% 距离衰减），**溅射对建筑伤害再乘 `buildingDamageMultiplier`**（BulletComp.java:122-145, 229-313; Damage.java:505-551, 659-661; Vars.java:133）。
7. `scaledHealth` 只是 `health=size²·scaledHealth` 的便捷折算，**Duo 直接用 `health=250` 不走 scaledHealth**（Block.java:1387-1403, Blocks.java:3323）。
8. 炮台**不用 Weapon 类**：`mindustry.type.Weapon` 只服务单位的武器挂载（WeaponMount）；炮台用 `Turret.shoot`（ShootPattern，多管=shots/shotDelay/交替 barrel）+ `drawer`（DrawBlock，RegionPart 纯表现）——#28 建模时别把两套 API 混在一起。
9. 索敌是「**单位优先，建筑其次**」两级：`Units.bestTarget` 先 `bestEnemy`（排序键 `unitSort`，默认距离²，`targetPriority` 优先级盖过距离），无单位才查建筑（`block.priority`，同优先级取近）（Units.java:284-293, 318-337; UnitSorts.java:9-18）。
10. 装填的"是否开火"判定用的是 `Angles.angleDist(rotation, targetRot) < shootCone`，枪口必须转到目标方向锥内；`rotation` 每 tick 以 `rotateSpeed·delta·efficiency` 逼近（Turret.java:597-604, 667-669）。

## 1. Turret / ItemTurret 生命周期

类继承链（源码事实）：`Block ← BaseTurret ← ReloadTurret ← Turret ← ItemTurret`（BaseTurret.java:20; ReloadTurret.java:9; Turret.java:36; ItemTurret.java:23）。每层只新增自己的关注点：

| 层级 | 职责 | 关键字段/方法 |
|---|---|---|
| BaseTurret | 基础属性、冷却液声明 | `range=80`(21)、`rotateSpeed=5`(23)、`coolantMultiplier=5`(32)、`coolant`(34)、`activationTime`(27) |
| ReloadTurret | reload 计数与冷却 | `reload=10`(10)、`reloadCounter`(26)、`updateCooling()`(28-39) |
| Turret | 索敌、旋转、射击管线、弹仓 | `target*/shootCone/inaccuracy/velocityRnd/…`(38-161)、`ammo Seq<AmmoEntry>`(288) |
| ItemTurret | 物品→弹药映射、入仓 | `ammoTypes: ObjectMap<Item,BulletType>`(24)、`handleItem/acceptItem`(155-189) |

### 1.1 单 tick 主循环（TurretBuild.updateTile, Turret.java:496-614）

时序（同一 tick 内按序）：

1. **目标校验**：`validateTarget()` 失效则清空 `target`（497; 校验规则见 Units.java:151-157 —— 超距/同队/不合法/不可被瞄准即失效）。
2. **开火意图**：`isShooting = alwaysShooting || (玩家控制 ? unit.isShooting() : 逻辑控制 ? logicShooting : target != null)`（498）。玩家/逻辑/默认 AI 三选一，互斥。
3. **warmup 插值**：目标是 `(isShooting && canConsume()) || charging() ? 1 : 0`（508-521），线性或曲线逼近 `shootWarmupSpeed`。
4. **视觉量衰减**：recoil、heat（`1/cooldownTime`）、charge（`1/firstShotDelay`，仅 charging 时上升）（525-533）。
5. **装填**：`handleReload()`（553，实现 721-728）：只要 `reloadCounter < reload` 且（`reloadWhileCharging || !charging()`）就 `updateReload()` + `updateCooling()`。
6. **索敌**：`hasAmmo()` 时每 `targetInterval`（有目标则 `newTargetInterval`，默认同为 20）tick 调 `findTarget()`（571-573）。
7. **射击判定**（575-613）：
   - 玩家：`targetPos = unit.aim`，`canShoot = unit.isShooting()`（579-580）
   - 逻辑：`canShoot = logicShooting`（582）
   - 默认 AI：`targetPosition(target)` 做提前量预测，`canShoot = within(target, range() + hitSize/1.9f)`（584-587，**注意是 1.9 不是 2**）
   - `targetRot = angleTo(targetPos)`；`shouldTurn()` 时不 charging 或 `moveWhileCharging` 才转（595-599）
   - **开火条件**：`!alwaysShooting && Angles.angleDist(rotation, targetRot) < shootCone && canShoot`，通过则 `updateShooting()`（601-604）；`alwaysShooting` 绕过锥角与目标直接射（609-612）。
8. **装填门**：`updateShooting()`（739-748）：`reloadCounter >= reload && !charging() && shootWarmup >= minWarmup` 才 `shoot(type)`，随后 `reloadCounter %= reload`。
9. **扣弹与造弹**：见 1.4。

旋转无独立 `updateRotation` 方法（源码全文无此符号）：旋转只在第 7 步由 `turnToTarget()` 完成 —— `rotation = Angles.moveToward(rotation, targetRot, rotateSpeed * delta() * potentialEfficiency)`（Turret.java:667-669）。

### 1.2 索敌（findTarget, Turret.java:641-665）

- 分支一：`targetAir && !targetGround`（纯对空）→ `Units.bestEnemy`，过滤 `!dead && !isGrounded && unitFilter`（642-643）。
- 分支二（默认）→ `Units.bestTarget`（647-649）：
  - 单位过滤：`!dead && unitFilter && (isGrounded || targetAir) && (!isGrounded || targetGround) && (missiles || !(e instanceof TimedKillc))`，其中 `missiles = ammo==null || ammo.targetMissiles`
  - 建筑过滤：`buildings = targetGround && targetBlocks && (ammo==null || ammo.targetBlocks)`，且 `buildingFilter`（默认 `targetUnderBlocks || !b.block.underBullets`，Turret.java:117）
- **双距离**：先按 `range()`（含弹种 `rangeChange`）找；没有结果且 `trackingRange() > range()` 时按 `trackingRange()` 再找一次（只锁定不射击，658-660）；对照 `findEnemyTile` 前还可对友方治疗（`canHeal()` 时找受损友方建筑，662-664）。
- 阵营过滤位置：`Units.bestEnemy`/`bestTarget`/`BlockIndexer.findEnemyTile` 内部按敌方队伍枚举，`Team.derelict` 直接返回 null（Units.java:285, 319; BlockIndexer.java:464-466）。
- **单位排序键**：`unitSort.cost(e,x,y)`，默认 `UnitSorts.closest = Unit::dst2`（UnitSorts.java:9-13）；比较规则：`cost < cdist` 或 `targetPriority 更高` 才替换，即**优先级（UnitType.targetPriority）压过距离**（Units.java:328-333）。`TargetPriority` 常量：wall=-3, under=-2, transport=-1, base=0, turret=1, core=2（TargetPriority.java:5-17）。
- **建筑排序**：`indexer.findEnemyTile` 遍历敌方队伍，取同队内 `findTile` 候选；跨队比较 `priority.priority(candidate)`（默认 `b.block.priority`，UnitSorts.java:22）：**高优先级无条件胜出，同优先级取距离近者**（BlockIndexer.java:471-480）。

### 1.3 弹道预测（targetPosition, Turret.java:459-479）

- `accurateDelay && !moveWhileCharging` 时按 `firstShotDelay` 时长把目标的位移外推进 offset（466-468）。
- `predictTarget && speed >= 0.01f` → `Predict.intercept(this, pos, offset, speed)`（470-474），否则直接取目标位置。
- `bullet.speed` 是预测用关键参数（Type.create 时也用它定初速）。

### 1.4 开火管线（shoot/bullet, Turret.java:750-825）

1. 弹口坐标：`x + Angles.trnsx(rotation-90, shootX, shootY)`（751-753；`shootY` 默认 `size*tilesize/2`，init 时定，212）。
2. `ShootPattern` 选择：`type.shootPattern != null ? type.shootPattern : shoot`（760）。
3. `pattern.shoot(barrelCounter, handler)`：每个子弹 `queuedBullets++`；`delay>0` 用 `Time.run(delay, …)` 延迟，否则同步立即生成（762-777）。
4. **扣账**：`consumeAmmoOnce=true`（默认）时在 `shoot()` 返回前 `useAmmo()` 一次（779-781）；false 时每发 `bullet()` 末尾各扣一次（822-824）。**注意：扣账发生在把手之前**（含 firstShotDelay>0 的蓄力弹 —— 扣了弹但子弹延迟 tick 才生成）。
5. `bullet()` 内：`xSpread = Mathf.range(xRand)`；`shootAngle = rotation + angleOffset + Mathf.range(inaccuracy + type.inaccuracy)`（789-793）；速度倍率 `(1-velocityRnd) + Mathf.random(velocityRnd) + extraVelocity`，寿命倍率 `(1-lifeRnd) + Mathf.random(lifeRnd) + extraLife`（795-799）。
6. `type.create(this, team, bulletX, bulletY, shootAngle, -1f, velocityScl, lifeScl, null, mover, aimX, aimY)` 生成 Bullet 实体（799）。
7. 表现侧：shootEffect/smokeEffect/sound/ammoUseEffect/shake/recoil/heat/totalShots++（801-820）。

`limitRange(margin)` 会**覆盖弹种 lifetime**：`lifetime = (range + bullet.rangeChange + margin + extraRangeMargin + 10) / bullet.speed`（Turret.java:248-252；ItemTurret 对全弹种循环调用，ItemTurret.java:42-46；Duo 调 `limitRange(5f)`，Blocks.java:3331）。

## 2. Weapon / Bundle 与 drawer：逻辑 / 表现切分

**术语澄清**：Mindustry 源码里没有炮台侧的 "Weapon" 概念。`mindustry.type.Weapon` 只用于**单位**（UnitType.weapons → WeaponMount，WeaponMount.java:8-18）；炮台的等同物是 `Turret.shoot`（ShootPattern）+ `Turret.drawer`（DrawBlock）。#41 所说的"多 weapon、shots/shotDelay"在炮台侧实为 ShootPattern 的三个字段。

### 2.1 ShootPattern（多管/连发，ShootPattern.java:7-26）

- `shots`（每次扳机子弹数，9）、`firstShotDelay`（首发延迟，11）、`shotDelay`（连发间隔，13）。
- 默认 `shoot()`：`shots` 发，第 i 发延迟 `firstShotDelay + shotDelay*i`（22-26）。
- 变体：`ShootAlternate`（炮管交替，`barrels`/`spread`/`barrelOffset`，ShootAlternate.java:7-14；Duo 用 `new ShootAlternate(3.5f)`，Blocks.java:3301 → 双管 x 偏移 ±1.75，按 `totalShots % 2` 交替）。
- `ShootPattern` 是纯逻辑（决定子弹出点/延迟）；管数、交替是逻辑量，但视觉 barrel 动画由 drawer 部件消费同一 `barrelCounter`。

### 2.2 Turret.drawer（DrawBlock → DrawTurret）

- `DrawTurret` 字段：`parts: Seq<DrawPart>`、`ammoParts`（按弹种切换部件组）、`basePrefix`、图层常量（DrawTurret.java:22-29）；`draw()` 里用 `build.recoilOffset、build.heat、build.drawrot()` 画底座/炮身/热区（74-141）。
- **纯表现**：base/top/heat/preview 全部是贴图与 transform（recoilOffset 由 `Mathf.pow(curRecoil, recoilPow) * recoil` 沿后坐方向平移，Turret.java:538 —— 输入是逻辑量 curRecoil，输出纯视觉）。

### 2.3 Weapon（单位侧，逻辑字段 vs 表现字段，Weapon.java:29-167)

| 类别 | 字段 | 备注 |
|---|---|---|
| 逻辑 | `bullet`(33)、`reload`(75)、`inaccuracy`(77)、`shootX/shootY`(91)、`xRand/yRand`(95)、`shoot`(97)、`velocityRnd`(101)、`extraVelocity`(103)、`lifeRnd`(105)、`extraLife`(107)、`shootCone`(109)、`minWarmup`(113)、`continuous`(53)/`alwaysContinuous`(55)、`alternate`(43)、`recoil`(81) 等 | 装填、散布、模式、射速全部进射击管线 |
| 逻辑(行为控制) | `rotate`(45)、`controllable`(59)、`aiControllable`(61)、`autoTarget`(65)、`predictTarget`(67)、`targetInterval/targetSwitchInterval`(71)、`rotateSpeed`(73)、`alwaysShooting`(63)、`noAttack`(123)、`min/maxShootVelocity`(125-127)、`shootOnDeath`(163) | 决定武器是否/如何自动射击 |
| 纯表现 | `name`(31 贴图名)、`ejectEffect`(35)、`display`(37)、`mirror`(39)、`flipSprite`(41)、`top`(51)、`shadow`(99)、`layerOffset`(133)、`region/heatRegion/cellRegion/outlineRegion`(147-153)、`parts`(167)、声音族(135-145) | 绘制/音效/镜像，不进伤害计算 |
| 混合 | `shootStatus/shootStatusDuration`(157-161，射击给自己上状态)、`aimChangeSpeed`(57，点激光用) | — |

### 2.4 RegionPart 与动画（RegionPart.java; DrawPart.java）

- RegionPart 字段全是表现量：`suffix/name/regions/outlines/heat`（15-21）、`mirror`(23)、`outline`(25)、`drawRegion`(29)、`heatLight`(31)、颜色族(47-48)、`children/moves`(49-50)。`draw()` 只做绘制（67-177）。
- **与逻辑的连接点**：`progress/growProgress/heatProgress` 是 `PartProgress` 函数，读取 turret 运行期状态：`reload`（开火后=1→0）、`smoothReload`、`warmup`、`charge`、`recoil`（无曲线）、`heat`（刚开火=1→冷却到 0）、`life`（导弹）、`time`（DrawPart.java:73-90）。即"动画" = 表现层采样逻辑层的装填/后坐/热值。
- 同类部件：ShapePart/HoverPart/EffectSpawnerPart/FlarePart/HaloPart（均在 `entities/part/`）。炮台侧 RegionPart 显式设 `turretShading=false`（BulletType.load, BulletType.java:406-410 的同类机制）。
- Duo 例子：两条 `-barrel-l/-barrel-r` 部件，`progress = PartProgress.recoil; recoilIndex = f; moveY = -1.5f`（Blocks.java:3304-3314）—— 后坐位移只来自逻辑量 curRecoils[barrel]。

## 3. BulletType 家族：字段语义与继承层差异

### 3.1 字段语义表（BulletType.java 为基类，行号见括号）

**运动**：`speed` 单位/ tick（37）、`lifetime` tick（33）、`drag` 每 tick 速度乘 `1-drag·delta`（49，BulletComp.java:164）、`accel` 每 tick 速度增量（51）、`weaveMag/weaveScale`（350/348）、`rotateSpeed` 转向（354）、`circleShooter*`（292-298）、`floatn`。
**命中伤害**：`damage`（41）、`splashDamage`(109)+`splashDamageRadius`(301，≤0 关闭)+`splashDamagePierce`(303)+`scaledSplashDamage`(111)；`hitSize`(43) 碰撞盒；`knockback`(113)+`impact`(115)；`maxDamageFraction`(61) 单次伤害上限=目标血量×系数；`killShooter`(105)。
**pierce 族**：`pierce`(53 传单位)、`pierceBuilding`(55)、`pierceCap`(57，≥1 时 init 里自动开 pierce，820-823)、`pierceDamageFactor`(59 每次穿刺扣 damage = 目标剩余血量×系数，523-532)、`removeAfterPierce`(63，damage≤0 或穿满 cap 即移除，528-531, BulletComp.java:190-193)、`pierceArmor`(187 无视护甲)、`armorMultiplier`(189)/`blockArmorMultiplier`(191) 护甲乘子。
**status 族**：`status`(117)+`statusDuration`(119) 直击施加；溅射半径内 `Damage.status`（597-599）。
**碰撞开关**：`collides`(131)、`collidesAir/collidesGround`(129)、`collidesTiles`(125)、`collidesTeam`(127)、`collideFloor`(133)、`collideTerrain`(135)、`hitUnder`(177)、`laserAbsorb`(65)；`hittable`(143 可被点防拦截)、`reflectable`(145)、`absorbable`(147)。
**frag 族**：`fragBullet`(202)+`fragBullets`(212)+`fragRandomSpread`(206)+`fragSpread`(208)+`fragAngle`(210)+`fragVelocityMin/Max`(214)+`fragLifeMin/Max`(216)+`fragOffsetMin/Max`(218)+`fragOnHit`(181)+`fragOnDespawn`(183)+`fragOnAbsorb`(185)+`pierceFragCap`(220)+`delayFrags`(204)。触发时机：命中 `hit()` 调 `createFrags`（555-561），或被移除时 `removed()` 里 `frags==0 && fragOnDespawn` 才补发（656-658）；frag 继承 owner/team/shooter 但 **keepVelocity 被强制关掉**（837-840）。
**射击参数（炮台消费）**：`inaccuracy`(93，与炮台 inaccuracy 叠加)、`ammoMultiplier`(95，入仓 1 物品→N 单位)、`reloadMultiplier`(97，乘装填速率)、`rangeChange`(157)/`minRangeChange`(163)/`extraRangeMargin`(159)、`scaleLife`(141 寿命按目标距离缩放)。
**范围派生**：`range = speed·lifetime`（无 drag 时），`rangeOverride`(155) 直接覆盖，`maxRange`(153) 下限保护（435-440）；`range` 在 `init()` 里定（402, 854）。
**特效/表现**：`hitEffect`(71)/`despawnEffect`(73)/`shootEffect`(75)/`chargeEffect`(79)/`smokeEffect`(81)/`hitColor`(241)/`trail*`(268-290)/`light*`(371-375)/`underwater`(237) 等，全部不进伤害。
**其他**：`healPercent/healAmount`(165/167)（`heals()` 判定 447-449）、`lifesteal`(173)、`makeFire`(175)、`lightning*`(333-345)、`incendAmount`(306)、`puddles*`(357-363)、`suppressionRange`(322)、`homingPower/Range/Delay`(313-317)、`followAimSpeed`(319)、`sticky`(193)+`stickyExtraLifetime`(195)、`keepVelocity`(137 继承射手速度)+`scaleKeepVelocity`(139)、`despawnHit`(179，init 时 frag/溅射/闪电自动置 true，832-834)、`shootPattern`(77) 覆盖炮台 pattern。
**生成替代**：`spawnUnit`(253) 生成单位代替子弹（create 里 919-948）、`despawnUnit`(255)；`spawnBullets`(247) 出生同步生成子弹（700-715）；`intervalBullet`(223)+`bulletInterval`(225) 飞行中周期性生成。

### 3.2 Bullet 实体生命周期（BulletComp.java + TimedComp.java）

- 生成：`create()` 里 `bullet.initVel(angle, speed·velocityScl·(velScaleRand))`、`lifetime = lifetime·lifetimeScl·(lifeScaleRand)`、`damage = (传参<0 ? this.damage : 传参)·damageMultiplier()`、`buildingDamageMultiplier` 拷贝，然后 `add()` 触发 `type.init(b)`（BulletType.java:950-978, 700-715；`add` 调 `init`，BulletComp.java:78-80）。
- 每 tick：`update()`（BulletComp.java:157-199）：位移 += vel·delta → 拖拽/accel → `type.update(b)`（homing/weave/trail/interval，717-723）→ 地面时 tile 射线碰撞（`tileRaycast`，186-188）。**寿命**：`TimedComp.update`：`time = min(time+delta, lifetime)`，`time >= lifetime` → `remove()`（TimedComp.java:15-21）。
- 移除：`remove()` 里 `!hit` 才走 `type.despawned(b)`（despawnHit 时按命中结算，637-648），无论何种原因都走 `type.removed(b)`（fragOnDespawn 补发 frag，651-659）；`collided` 清空（83-92）。
- 单位碰撞：`collides()` 过滤阵营/空地/重复穿刺（114-120）；`collision()` → `type.hit(x,y)`（fx+溅射+frag）→ 非 pierce 则移除 → `type.hitEntity`（122-145）。pierce 单位只记录 collided 不移除。
- 建筑碰撞：`tileRaycast` 逐格推进，命中满足 `checkUnderBuild`+`collide()`+`testCollision()`+异队+未穿过的建筑：`build.collision(b)` 扣血（内部 `type.buildingDamage(b)=b.damage·buildingDamageMultiplier`，再经 `Damage.applyArmor(damage, block.armor·armorMultiplier·blockArmorMultiplier)`，BuildingComp.java:1762-1778），非 pierceBuilding 则移除；`type.hitTile`（290）。

### 3.3 BasicBulletType（实体贴图弹，BasicBulletType.java）

新增字段全是表现：`backColor/frontColor`(13)、`mixColorFrom/To`(14)、`width/height`(15)、`shrinkX/shrinkY`(16)+`shrinkInterp`(17)、`spin`(18)、`rotationOffset`(18)、`sprite/backSprite`(19-20)。`draw()` 按 `b.fout()` 收缩、按 `b.time·spin` 旋转（48-68）。

### 3.4 继承层差异清单（派生类只新增这些，其余继承）

| 类 | 基类 | 差异字段 / 行为 |
|---|---|---|
| BasicBulletType | BulletType | 贴图绘制族（见 3.3） |
| ArtilleryBulletType | BasicBulletType | `trailMult/trailSize`(8)；构造默认 `collidesTiles=false, collides=false, collidesAir=false, scaleLife=true, hitShake=1`、斜降 trail 特效（10-40） |
| FlakBulletType | BasicBulletType | `explodeRange/explodeDelay/flakDelay/flakInterval`(9)；update 里近敌引爆（time=lifetime 触发 despawnHit 溅射），`collidesGround=false`（25-47） |
| MissileBulletType | BasicBulletType | 构造默认 `homingPower=0.08, trailChance=0.2, lifetime=52`（8-19） |
| BombBulletType | BasicBulletType | 自杀式炸弹（供返回舱核弹，无新增逻辑字段；BombBulletType.java:6） |
| MassDriverBolt | BasicBulletType | 质量驱动器弹丸（MassDriverBolt.java:13） |
| InterceptorBulletType | BasicBulletType | 拦截者导弹（InterceptorBulletType.java:8） |
| LiquidBulletType | BulletType | `liquid/puddleSize/orbSize/boilTime`(17-20)；`ammoMultiplier=1`、`statusDuration=120`、`drag=0.001`（22-43）；update 产水坑、despawned/hit 覆盖（49-105） |
| FireBulletType | BulletType | `colorFrom/Mid/To, radius, velMin/Max, fireTrailChance`(12-17)；默认 `pierce=true, collidesTiles=false, collides=false`（19-26）；update 随机点火 |
| ShrapnelBulletType | BulletType | `length/width/serrations…`(13-19)；默认 `speed=0, collides=false, pierce=true, hittable=false`（21-33）；init 即 `Damage.collideLaser` 全段伤害，`range=length`（35-52） |
| LaserBulletType | BulletType | `colors/length/width/lengthFalloff/sideLength/sideWidth`(13-18) |
| LightningBulletType | BulletType | 闪电（LightningBulletType.java:9） |
| EmpBulletType | BasicBulletType | `radius/timeIncrease/timeDuration/powerDamageScl/powerSclDecrease/…`(9-14)；hit 里范围内过载/减速/伤害建筑与单位（17-65） |
| ContinuousBulletType | BulletType | `length/shake/damageInterval/largeHit/continuous=true`(9-13)；持续光束（每 damageInterval tick 结算） |
| ContinuousFlameBulletType | ContinuousBulletType | 火焰外观 `lightStroke/width/oscScl/oscMag/divisions/drawFlare/flareColor`(15-20) |
| ContinuousLaserBulletType | ContinuousBulletType | `fadeTime/lightStroke/divisions/colors/strokeFrom/To`(13-17) |
| RailBulletType | BulletType | `length/pointEffectSpace`(13-15)；轨道炮，覆盖 testCollision（84） |
| PointBulletType | BulletType | `trailSpacing`(13) |
| PointLaserBulletType | BulletType | `color/beamEffect*/oscScl/oscMag/damageInterval/shake`(20-28) |
| SapBulletType | BulletType | `length/lengthRand/sapStrength/color/width`(15-18) |
| ExplosionBulletType | BulletType | 模板：`hittable=false, lifetime=1, speed=0, instantDisappear=true, killShooter=true, collides=false`（17-28） |
| SpaceLiquidBulletType | BulletType | `orbSize`(11)，太空液体 |
| MultiBulletType | BulletType | `repeat`(12)，create 里连发多个子弹（45） |
| EmptyBulletType | BulletType | 空弹占位（EmptyBulletType.java:3） |

（类清单与目录实体一致：`ref/mindustry/core/src/mindustry/entities/bullet/`。）

### 3.5 射击管线里 ammoMultiplier / reloadMultiplier / rangeChange / velocityRnd 的消费点

| 字段 | 消费点（源码坐标） | 语义 |
|---|---|---|
| `ammoMultiplier` | 入仓：`totalAmmo += type.ammoMultiplier`、条目 `amount += type.ammoMultiplier`（ItemTurret.java:168,176,183）；扣账：`entry.amount -= ammoPerShot`（Turret.java:687） | 物品→弹药单位换算；液体弹反向 `1/ammoMultiplier` 体积/发（LiquidTurret.java:124,135） |
| `reloadMultiplier` | `ammoReloadMultiplier() = hasAmmo() ? peekAmmo().reloadMultiplier : 1`（Turret.java:734-737），乘进 `updateReload`（731）与 `updateCooling`（ReloadTurret.java:33） | 当前弹种影响装填速度（Duo：石墨 0.8、硅 1.5，Blocks.java:3277,3288） |
| `rangeChange` | `range() = range + peekAmmo().rangeChange`（Turret.java:348-353）；`minRange()` 同理（340-345）；`trackingRange()`（355-357）；init 里 `placeOverlapRange` 预置（ItemTurret.java:95-97） | 弹种级射程增量；限距时进 lifetime 折算（Turret.java:249-251） |
| `velocityRnd`（炮台侧） | `bullet()`：`(1-velocityRnd)+Mathf.random(velocityRnd)+extraVelocity` 作为 `create` 的 velocityScl（Turret.java:799） | 初速随机比（0=恒速，1=0~2 倍） |
| `inaccuracy`（炮台+弹种） | `shootAngle = rotation + angleOffset + Mathf.range(inaccuracy + type.inaccuracy)`（Turret.java:793） | 两者相加后均匀随机取角 |
| `lifeRnd/extraLife` | `baseLife = (1-lifeRnd)+random(lifeRnd)+extraLife`（795-796） | 寿命随机/加成 |
| `scaleLife` | `lifeScl = clamp((baseLife+scaleLifetimeOffset)·dst/type.range, minRange()/type.range, range()/type.range)`（796） | 炮击按目标距离定寿命 |

## 4. Magazine / Ammo 数据流

### 4.1 容器模型

- `TurretBuild.ammo: Seq<AmmoEntry>`（Turret.java:288）+ `totalAmmo`（289）。`AmmoEntry` 抽象：`amount` + `type()`（263-267）。
- 条目实现族：`ItemEntry(item, amount)`（ItemTurret.java:226-246）—— 物品弹；LiquidTurret 不走 AmmoEntry，直接拿 `liquids.current()`（LiquidTurret.java:121-136）；PayloadAmmoTurret 用 `payloads` 队列 + `currentAmmo()`（PayloadAmmoTurret.java:94-140）。
- `ammoTypes` 映射：ItemTurret 是 `ObjectMap<Item,BulletType>`（默认 `OrderedMap`，ItemTurret.java:24）；LiquidTurret 是 `ObjectMap<Liquid,BulletType>`（LiquidTurret.java:19）。

### 4.2 入仓 → 折算（ItemTurretBuild, ItemTurret.java:155-189）

1. `acceptItem`：`ammoTypes.get(item) != null && totalAmmo + ammoMultiplier <= maxAmmo`（187-189）—— **单位制**：容量按弹药单位计，不是格数。
2. `handleItem`：`totalAmmo += ammoMultiplier`；已有同物品条目则 `amount += ammoMultiplier` 并 **swap 到队尾**；否则 `ammo.add(new ItemEntry(item, ammoMultiplier))`（166-183）。
3. `acceptStack`：`min((maxAmmo-totalAmmo)/ammoMultiplier, amount)`（133-139）；`handleStack` 逐件调 handleItem（142-146）。

### 4.3 选弹规则（peek 语义，对应 #41 所称 peekAmmoType）

- `peekAmmo() = ammo.size==0 ? null : ammo.peek().type()`（Turret.java:695-697）—— 恒取队尾。
- **后入为主（LIFO）**：新弹种 append、已有弹种 swap 到末尾，所以最后入仓的弹种优先被射（ItemTurret.java:171-183）。
- `hasAmmo()`（Turret.java:700-715）：队尾条目不足 `ammoPerShot` 时，向前找一个足量条目 swap 到队尾（702-709）；`!canConsume()`（断电/热不足）返回 false（712）；最终 `ammo.size>0 && (peek.amount>=ammoPerShot || cheating)`（714）。
- 扣账 `useAmmo()`（683-692）：`entry.amount -= ammoPerShot`；`<=0` 则 pop；`totalAmmo -= ammoPerShot`（下限 0）。**时机**：见 1.4 第 4 条（consumeAmmoOnce 时在 shoot() 尾部，即开火门已过、子弹出膛前）。
- 逻辑传感器：`currentAmmoType → ammo.peek().item`、`ammo → totalAmmo`、`ammoCapacity → maxAmmo`（ItemTurret.java:114-120; Turret.java:412-415）。

### 4.4 ammoUseEffect 与 UI

- `ammoUseEffect` 默认 `Fx.none`（Turret.java:126），在 `bullet()` 里以弹口后侧 `ammoEjectBack` 偏移播放（805-810）；Duo 用 `Fx.casing1`（抛壳，Blocks.java:3322）。
- 弹药条：`totalAmmo/maxAmmo`（ItemTurret.java:61-67, 128-130）；状态条 "noInput" 当 `enabled && !hasAmmo()`（Turret.java:374-379）。
- 存档：ammo 列表随 building 序列化，非法弹种被丢弃（ItemTurret.java:196-223）。

## 5. Coolant：consumeCoolant 与 coolantMultiplier 实际公式

### 5.1 声明与"可选"语义

- `BaseTurret.coolant: ConsumeLiquidBase`，`coolantMultiplier=5`（BaseTurret.java:32-34）；`ConsumeCoolant` 是 `ConsumeLiquidFilter` 的预置过滤器：`liquid.coolant && !liquid.gas && temperature<=0.5 && flammability<0.1`，量 `amount`（ConsumeCoolant.java:4-22）。
- `checkInitCoolant()`：强制 `update=false, booster=true, optional=true` 并加入 consumes（BaseTurret.java:70-78）——**冷却液是可选增压，缺液不阻止开火**。
- `handleLiquid` 里首次进冷却液触 `Trigger.turretCool`（Turret.java:616-623）。

### 5.2 每 tick 装填速率（ReloadTurret.java:25-47, BuildingComp.java:1928-1935）

基础（无冷却）：`updateReload()`：`reloadCounter += delta() * ammoReloadMultiplier() * baseReloadSpeed()`，其中 `delta() = Time.delta·timeScale`，`baseReloadSpeed() = efficiency`（ReloadTurret.java:45-47, 731; BuildingComp.java:1928-1930）。

冷却加成：`updateCooling()`（ReloadTurret.java:28-39）：

```
if(coolant != null && coolant.efficiency(this) > 0 && efficiency > 0):
    capacity = (ConsumeLiquidFilter ? 当前液 heatCapacity : coolant.consumes(liquids.current()) ? heatCapacity : 0.4)
    amount   = coolant.amount * coolant.efficiency(this)
    coolant.update(this)                        # 实际扣液：liquids.remove(liq, amount·edelta·multiplier) (ConsumeLiquidFilter.java:47)
    reloadCounter += amount * edelta() * capacity * coolantMultiplier * ammoReloadMultiplier()
```

其输入定义：
- `coolant.efficiency(b) = liq!=null ? min(liquids.get(liq)/(amount·edelta·multiplier), 1) : 0`（ConsumeLiquidFilter.java:52-57）—— 存量不足才掉效率，满液时 =1。
- `edelta() = efficiency·delta()`（BuildingComp.java:1933-1935）。
- `heatCapacity`：水 0.4、低温液(cryofluid) 0.9、焦油 0.7、腐肉瘤 0.4（Liquids.java:14,32,41,50；默认 0.5，Liquid.java:42）。

综合单 tick 装填增量（满效率、timeScale=1、efficiency=1）：

```
Δreload = ammoReloadMultiplier · (1 + coolant.amount · heatCapacity · coolantMultiplier)
```

实际射速 = `reload / Δreload` tick/发。[推导自上述公式]

**Duo 例算**（`reload=20, coolant=consumeCoolant(0.1f), coolantMultiplier=10`，Blocks.java:3319,3326-3327；铜弹 `ammoReloadMultiplier=1`）：
- 无水：20 tick/发。
- 水（0.4）：Δ=0.1·0.4·10=0.4 → 1.4/tick → 20/1.4 ≈ **14.3 tick/发**。
- 低温液（0.9）：Δ=0.1·0.9·10=0.9 → 1.9/tick → 20/1.9 ≈ **10.5 tick/发**。
- 液体消耗速率：0.1 单位/tick = 6 单位/秒（满效率）。

### 5.3 桶灌换算提示（给 MTurrets 的开放问题）

Mindustry 液体单位是纯游戏单位（1 单位≈铺 1 格 1 层的体积概念上），无现实体积定义，代码里只有 `amount·edelta` 的消耗速率。Minecraft 桶=1000mB 的换算系数属于本 mod 设计决策，本文件**不预设**（见"未证实项"）。

## 6. Health / scaledHealth 与伤害承受路径

### 6.1 scaledHealth 语义（Block.java）

- `scaledHealth`（188，默认 -1）：**每格占地血量**。init 时若 `scaledHealth < 0` 先置 40 并按造价给缩放（1388-1398，缩放逻辑为按需求材料的运行时代码，未追），再 `health = (int)(size·size·scaledHealth)`（1400-1402）；`health > 0` 直接覆盖 scaledHealth（190）。即 `health = size²·scaledHealth`，`scaledHealth` 只是内容作者侧的折算便利。
- **Duo 是显式 `health = 250`，不用 scaledHealth**（Blocks.java:3323）。
- 其他炮台示例（已核对块名与行号）：scatter `scaledHealth=200`（Blocks.java:3411）、lancer `280`（3575）、swarmer `300`（3723）、salvo `240`（3829）、fuse `220`（3930）。

### 6.2 伤害承受路径（Building）

- 直击（子弹命中建筑）：`BuildingComp.collision(Bullet)`（1762-1778）：`damage = type.buildingDamage(b)`（= `b.damage·buildingDamageMultiplier`，BulletType.java:661-663）；`!pierceArmor` 时 `Damage.applyArmor(damage, block.armor·t.armorMultiplier·t.blockArmorMultiplier)`（1766-1769）；`damage(other, other.team, damage)` → `damage(float)`（1786-1787 → 2064-2087）。
- `Building.damage(float)`（2064-2087）：`dead()` 直接返回；按 `state.rules.blockHealth(team)` 除权（server 侧倍率）；`health -= handleDamage(damage)`；`health<=0` → `Call.buildDestroyed`。`handleDamage` 基类恒等（1744-1746，多层建筑/护盾类覆盖）。
- 溅射（爆炸半径）：`Damage.damage(team, x, y, radius, splashDamage·b.damageMultiplier(), …)`（BulletType.java:593-595）→ 单位逐个体 `calculateDamage` 距离衰减；建筑走 `tileDamage`，其伤害乘 `source.type.buildingDamageMultiplier`（Damage.java:546）。**溅射与直击是两条独立路径**，同一爆炸对单位/建筑各结算一次。
- 护甲保底：`applyArmor = max(damage - armor, minArmorDamage·damage)`，`minArmorDamage = 0.1`（Damage.java:659-661; Vars.java:132-133）—— 至少 10% 伤害。
- 溅射衰减：`damage · lerp(1 - dist/radius, 1, 0.4)`，中心 100%、边缘 40%（Damage.java:652-655）。
- 单位侧伤害入口是对称的 `hitEntity`（BulletType.java:480-521）与 `HealthComp.damage` 族（HealthComp.java:51-101），本 mod 仅实体/爆炸伤害扣结构 Health 的边界对照即在此（DamageReceiver 概念在源码里不存在，见"未证实项"）。

## 未证实项 / 术语澄清

- **wiki 不可达**（mindustry.fandom.com 两次连接失败）：本次无 wiki 交叉对照，全部为源码结论；「wiki 与源码不一致处」无法产出，待网络可用后补。
- `DamageReceiver`：dc32943 源码全文无此符号，搜不到；伤害入口实际是 `Building.damage(float)` / `BulletType.hitTile/hitEntity` / `Damage.damage` 三条。
- `updateRotation`：源码无此方法，#41 描述中的 `rotate/updateRotation` 对应实际是 `turnToTarget()`（Turret.java:667-669）+ 玩家/逻辑控制直写 `unit.aim`。
- `peekAmmoType`：dc32943 里方法名是 `peekAmmo()`（Turret.java:695-697）（LiquidTurret 才叫 `peekAmmo` 同名牌，Turret 无 peekAmmoType）。
- Block.scaledHealth 的"按造价缩放"具体公式（Block.java:1391-1397 的 `scaling` 计算）未展开追读，文档只断言了 `health = size²·scaledHealth` 结论。
- `MultiBulletType.create`（MultiBulletType.java:45）与 `MassDriverBolt`/`InterceptorBulletType`/`BombBulletType` 的新增行为字段未逐行展开（类小，主要为 create/draw 覆盖）。
- 冷却液 `liquidEfficiencyMultiplier` 默认恒 1（ConsumeLiquidFilter.java:91-93），子类有无覆盖未逐一追验。
- 桶灌换算系数（Mindustry 液体单位 ↔ mB）留待 #28/#31 设计决策，本文件不预设立场。
- `Predict.intercept` 的解析实现未展开（Turret.java:471 仅调用点），如需移植到 Minecraft 弹道模型需另行读 `Predict.java`。

## 坐标速查（本文件引用文件）

- `ref/mindustry/core/src/mindustry/world/blocks/defense/turrets/{BaseTurret,ReloadTurret,Turret,ItemTurret,LiquidTurret,PayloadAmmoTurret}.java`
- `ref/mindustry/core/src/mindustry/entities/bullet/{BulletType,BasicBulletType,ArtilleryBulletType,FlakBulletType,MissileBulletType,LiquidBulletType,FireBulletType,EmpBulletType,ShrapnelBulletType,LaserBulletType,ContinuousBulletType,ContinuousFlameBulletType,ContinuousLaserBulletType,RailBulletType,PointBulletType,PointLaserBulletType,SapBulletType,ExplosionBulletType,LightningBulletType,MultiBulletType,EmptyBulletType,BombBulletType,MassDriverBolt,InterceptorBulletType,SpaceLiquidBulletType}.java`
- `ref/mindustry/core/src/mindustry/entities/comp/{BulletComp,BuildingComp,TimedComp,HealthComp}.java`
- `ref/mindustry/core/src/mindustry/entities/{Units,UnitSorts,Damage,TargetPriority}.java`
- `ref/mindustry/core/src/mindustry/entities/pattern/{ShootPattern,ShootAlternate}.java`
- `ref/mindustry/core/src/mindustry/entities/part/{RegionPart,DrawPart}.java`
- `ref/mindustry/core/src/mindustry/entities/units/WeaponMount.java`
- `ref/mindustry/core/src/mindustry/type/{Weapon,Liquid}.java`
- `ref/mindustry/core/src/mindustry/world/Block.java`、`ref/mindustry/core/src/mindustry/world/draw/DrawTurret.java`、`ref/mindustry/core/src/mindustry/world/consumers/{ConsumeCoolant,ConsumeLiquidFilter}.java`
- `ref/mindustry/core/src/mindustry/ai/BlockIndexer.java`、`ref/mindustry/core/src/mindustry/content/{Blocks,Liquids}.java`、`ref/mindustry/core/src/mindustry/Vars.java`