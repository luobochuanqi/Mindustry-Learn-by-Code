# Mindustry 子弹 FX（粒子特效）事实文档

> 用途：供 MTurrets FX 实现票（hit-burst + muzzle-flash 对齐）直接引用。
> 范围：只记录 Mindustry 机制事实，MTurrets 设计结论仅限第 3 节的评估与建议（标注清楚，不作最终决策）。
> 一手来源：`ref/mindustry`（稀疏检出，commit `dc32943612553832348d95f3ba5b7fe9b00d5099`，issue 所称 dc32943；已核对 `.git/logs/HEAD` 与 `.git/packed-refs`）。所有结论带 `(ref/mindustry/...:行)` 坐标（相对仓库根）。
> 校对规则：与 `docs/research/mindustry-turret-mechanics.md`（子弹/炮台逻辑管线）互补；该文档已覆盖 Bullet 实体生命周期与射击管线，本文件只补 FX 渲染侧，不重复推导。
> 时间语义：Mindustry 帧率 60 tick/s，"每 tick" = 1/60 秒；Effect 构造函数的第一参即 lifetime（tick）。世界单位：1 格 = 1 tilesize = 8 单位（Vars.java:135）。

## 给 FX 实现票的要点提要

1. 三个特效槽位全部是 **BulletType 数据字段**（`hitEffect`/`despawnEffect`/`shootEffect`/`smokeEffect`/`hitColor`，BulletType.java:71-81,241），内容作者在弹种定义里按弹药覆写；渲染器（`Fx.java` 里的 Effect 定义）不读弹药类型，只消费 `Effect.at(x, y, rotation, color)` 传入的四参。
2. **颜色来源是 `hitColor` 字段，不是子弹贴图色**：命中/消失/开火/烟四个特效全部以 `type.hitColor` 为 color 参数（BulletType.java:550,644;Turret.java:801-802）；Duo 铜弹 `hitColor = backColor = #d39169`、Scatter 铅弹 `hitColor = 默认白色`、Scatter 玻璃弹 `hitColor = frontColor = #ffffff`。**玻璃弹纯白 = 上游故意让特效色"不可见渐变"**。
3. 三种入射特效的**形状差异在"特效选型"而非颜色**：铜弹命中 = `hitBulletColor`（消费 e.color 的渐隐环+5 条火花线），铅/玻璃命中 = `flakExplosion`（**完全忽略 e.color** 的固定三色爆团，弹种间渲染完全相同）；消失特效三弹各不相同。
4. 枪口特效**三个弹种完全相同**（`shootSmall` 双三角 + `shootSmallSmoke` 5 粒灰烟，均固定调色板、吞掉 color 参数）；唯一按炮台区分的是 `ammoUseEffect`：Duo 抛壳 `casing1`、Scatter 无（`Fx.none`）。
5. FX 是纯客户端表现：`Effect.create` 有 `!headless && renderer.enableEffects` 门控 + 50 单位 clip 相机剔除（Effect.java:141-148）；`Fx.none` 直接不生成。
6. 上游特效**自带固定 lifetime/数量/尺寸**（`new Effect(14, e -> …)` 里硬编码，见第 2 节表），弹药只选特效名 + 给 color；MTurrets 若做数据驱动，字段量级 = 2 个特效名 + 1 个颜色（见第 3 节量化）。

## 1. 三种特效的上游定义与生成点

### 1.1 特效系统的数据流（Effect 基类）

- `Effect` 实例自带 `lifetime`（构造第一参，默认 50）、`clip`（默认 50，相机剔除半径）、`layer`（默认 `Layer.effect` = 110，可 `.layer(...)` 覆写）（Effect.java:31,33,43；Layer.java:75）。
- 渲染期字段：`id`（全静态递增的种子）、`color`（`.at()` 传入的 color 原样 set 到 EffectState，渲染回调读 `e.color`）、`rotation`、`time/lifetime`、`x/y`（Effect.java:168-172）。
- 门控：`shouldCreate()` = `!headless && this != Fx.none && renderer.enableEffects`，且相机盒与 `(x, y, clip)` 盒重叠才创建（Effect.java:141-148）。
- 渲染：`Draw.z(layer)` 后跑回调（Effect.java:179）；回调里 `color(...)`（多色插值）、`e.scaled(n, …)`（前 n tick 子渲染）、`randLenVectors(seed, count, len, …)`（count 条随机向量）都是 arc 工具，arc 源码不在稀疏检出内【未证实：`randLenVectors` 的具体随机分布实现，但调用点可确认参数 = 数量/长度/（角度+散布）】。

### 1.2 命中特效（hitEffect）

- **定义**：`BulletType.hitEffect`，默认 `Fx.hitBulletSmall`（BulletType.java:71）。
- **生成点**：`hit()` → `hitEffect.at(x, y, b.rotation(), hitColor)`（BulletType.java:550，x/y 是传入的命中点；命中后若 `!pierce` 子弹被移除，故一发至多一次 hit 特效）。
- **触发路径**（FX 视角，逻辑细节见 mechanics 文档）：
  - 撞实体：`BulletComp.collision` → `type.hit(self(), x, y)`（BulletComp.java:133）。
  - 撞建筑：`tileRaycast` → `build.collision(b)` → `type.hitTile(...)`，直击分支（异队且 direct）→ `hit(b)`，座标用子弹当前 `b.x/b.y` 而非传参（BulletType.java:457-478,469）。
  - 弹到寿（`despawnHit=true` 时）：`despawned()` → `hit(b, b.x, b.y, false)`（BulletType.java:637-642）；`despawnHit` 默认 false，但 `init()` 里 **`fragBullet != null || splashDamageRadius > 0 || lightning > 0` 自动置 true**（BulletType.java:179,831-835）——Scatter 三个弹种都有溅射，所以**到寿引爆走的也是 hitEffect**。
- **颜色参数**：恒为 `type.hitColor`（550）。吃不吃它看特效自身：`hitBulletColor` 吃；`hitBulletSmall`/`flakExplosion` 不吃。

### 1.3 消失特效（despawnEffect）

- **定义**：`BulletType.despawnEffect`，默认 `Fx.hitBulletSmall`（BulletType.java:73）。
- **生成点**：`despawned()`：`despawnHit` 时先 `hit(...)`（引爆，见 1.2），随后无条件 `despawnEffect.at(b.x, b.y, b.rotation(), hitColor)`（BulletType.java:644，座标恒为子弹当前位置）。
- **触发路径**：寿命到（TimedComp `time >= lifetime` → remove，TimedComp.java:15-21）或外部移除时 `remove()` 里 `!hit` 才调 `type.despawned`（BulletComp.java:87-91；命中过的子弹不触发 despawnEffect）。
- **注意**：despawnHit 的弹药（全部 Scatter 弹）消失时 **hitEffect 和 despawnEffect 同点连发**——引爆爆团 + 消失特效叠在一起（BulletType.java:638-644）。

### 1.4 枪口特效（shootEffect / smokeEffect / ammoUseEffect）

- **定义**：`BulletType.shootEffect` 默认 `Fx.shootSmall`（75）、`smokeEffect` 默认 `Fx.shootSmallSmoke`（81）、`chargeEffect` 默认 `Fx.none`（79，仅 `shoot.firstShotDelay > 0` 时生成，Turret.java:755-758；Duo/Scatter firstShotDelay=0 故不触发）；炮台侧可整体覆盖 `Turret.shootEffect/smokeEffect`（默认 null = 用弹种值，Turret.java:122,124）。
- **生成点**（`TurretBuild.bullet()`，每发子弹调用一次）：
  - 枪口：`bulletX/bulletY = 炮台中心 + trns(rotation-90, shootX + xOffset + xSpread, shootY + yOffset)`（Turret.java:790-793）；`shootX=0`、`shootY` 默认 `size*tilesize/2`、Duo 覆写 `shootY=3f`（Turret.java:72,212;Blocks.java:3318）。
  - `shootEffect.at(bulletX, bulletY, rotation + angleOffset, type.hitColor)`（Turret.java:801）——rotation 带管偏移角，颜色是 type.hitColor。
  - `smokeEffect.at(同点, 同角, type.hitColor)`（Turret.java:802）。
  - `ammoUseEffect.at(x - trnsx(rotation, ammoEjectBack), y - trnsy(...), rotation * sign(xOffset))`（Turret.java:805-809）——炮台中心**后方** `ammoEjectBack`（默认 1f，Turret.java:140）处，旋转值按管侧取符号（抛壳朝哪一侧由 xOffset 决定）。
- **每扳机次数**：`ShootPattern.shoot` 每颗子弹调一次 handler（shots 发，延迟 firstShotDelay + shotDelay·i，ShootPattern.java:19-26）；Duo = `ShootAlternate(3.5f)`（shots=1，两管 xOffset = ±1.75 交替，指数 `(totalShots % barrels) - (barrels-1)/2`，ShootAlternate.java:11-26;Blocks.java:3301）；Scatter = 默认 pattern（shots=2, shotDelay=5 → 两发同点、间隔 5 tick，xOffset=0，Blocks.java:3403-3404;ShootPattern.java:20-26）。

### 1.5 各特效的渲染细节（Fx.java 定义，全部硬编码在定义内）

| Fx 名 | lifetime | 形状与"数量" | 颜色（color() 序列） | layer | 是否消费 e.color |
|---|---|---|---|---|---|
| `hitBulletSmall`（855-871） | 14 | 前 7 tick：渐隐环（stroke 0.5+fout，半径 fin×5）；全程：5 条随机方向火花线（长度 fin×15，线宽 fout×3+1）；光晕 r=20 α=0.6·fout（Drawf.light） | white → lightOrange(#f68021) | 110（默认） | **否**（固定调色板） |
| `hitBulletColor`（873-889） | 14 | 同上（环 + 5 火花线 + 光晕 r=20） | white → e.color；光晕也用 e.color | 110（默认） | **是**（颜色渐变尾色 + 光晕） |
| `flakExplosion`（1163-1185） | 20 | 前 6 tick：爆环（stroke 3·fout，半径 3+fin×10）；5 个灰色实心圆（半径 fout×3+0.5，长度 2+23·finpow）；4 条淡橙火花线（长度 1+23·finpow，线宽 1+fout×3）；光晕 r=50 α=0.8·fout | bulletYellow(#fff8e8) → gray → lighterOrange(#f6e096) | 110（默认） | **否**（完全固定） |
| `shootSmall`（1824-1829） | 8 | 2 个三角（Drawf.tri）：前向（宽 1+5·fout，长 15·fout）+ 后向（长 3·fout） | lighterOrange(#f6e096) → lightOrange(#f68021) | 110（默认） | 否（吞掉 color 参数） |
| `shootSmallSmoke`（1852-1858） | 20 | 5 个灰烟圆（半径 fout×1.5，长度 finpow×6，**角度绕 e.rotation ±20°**） | lighterOrange → lightGray → gray | 110（默认） | 否 |
| `casing1`（2227-2241） | 30 | 1 个 1×2 弹壳矩形（alpha e.fout(0.3)；沿 \|rotation\|+90° 抛出 2-8 单位 + 随机 ±3，旋转 fin×50·sign） | lightOrange(#f68021) → lightGray → lightishGray(#a2a2a2) | **100 = Layer.bullet**（2241 覆写） | 否 |
| `none`（31） | 0 | 空回调；clip=0 | — | — | — |

（数量 = 明确 count 的随机向量/形状数；环/三角不属于"颗粒数"。）

## 2. 三种弹药的特效参数表

### 2.1 Duo — 铜弹（`BasicBulletType(2.5f, 9)`，Blocks.java:3262-3271）

| 特效槽 | Fx 名 | 来源 | 颜色 | 尺寸/形状 | lifetime | 数量 | layer | 生成点 |
|---|---|---|---|---|---|---|---|---|
| hitEffect | `Fx.hitBulletColor` | 弹种显式覆写（3268） | 渐变 white→**#d39169**（=Pal.copperAmmoBack，3269） | 环半径 ≤ fin×5；火花线 ≤ fin×15、宽 fout×3+1；光晕 r=20 | 14 | 5 火花线 + 环 | 110 | 命中点（碰撞点或 b.x/b.y） |
| despawnEffect | `Fx.hitBulletColor` | 同上（同赋值 3268） | 同上 | 同上 | 14 | 同上 | 110 | 子弹当前位置（BulletType.java:644） |
| shootEffect | `Fx.shootSmall` | 默认（BulletType.java:75） | 固定 f6e096→f68021（**color 参数=#d39169 被吞掉**） | 双三角 15/3×fout 长 | 8 | 2 三角 | 110 | 枪口（shootY=3f + 管偏移 ±1.75 交替；Turret.java:791,801） |
| smokeEffect | `Fx.shootSmallSmoke` | 默认（81） | 固定灰系（吞掉 color） | 圆 ≤ finpow×6 长、fout×1.5 半径 | 20 | 5 圆 | 110 | 同枪口（802） |
| ammoUseEffect | `Fx.casing1` | 炮台显式（Blocks.java:3322） | 固定橙灰系 | 1×2 矩形、抛出 2-8 单位 | 30 | 1 | **100** | 炮台中心后 1 单位处（Turret.java:805-809；ammoEjectBack=1） |

每扳机：1 发 → shoot+smoke 各 1 次（Duo shots=1）。

### 2.2 Scatter — 铅弹（`FlakBulletType(4.2f, 3)`，Blocks.java:3352-3361）

| 特效槽 | Fx 名 | 来源 | 颜色 | 尺寸/形状 | lifetime | 数量 | layer | 生成点 |
|---|---|---|---|---|---|---|---|---|
| hitEffect | `Fx.flakExplosion` | 弹种显式覆写（3358） | 固定 fff8e8→gray→f6e096（**e.color=白 被忽略**） | 爆环 3+fin×10；圆 ≤ 2+23·finpow；4 线 ≤ 1+23·finpow；光晕 r=50 | 20 | 5 圆 + 4 线 | 110 | 命中点（550） |
| despawnEffect | `Fx.hitBulletSmall` | **默认值未覆写**（3352-3361 无 despawnEffect 行 → BulletType.java:73） | 固定 white→f68021 | 环 ≤ fin×5；5 线 ≤ fin×15；光晕 r=20 | 14 | 5 线 + 环 | 110 | 子弹当前位置（644） |
| shootEffect | `Fx.shootSmall` | 弹种显式（3355，与默认同值） | 固定橙系（吞 color=白） | 双三角 | 8 | 2 三角 | 110 | 枪口（shootY=8 默认 + xOffset=0） |
| smokeEffect | `Fx.shootSmallSmoke` | 默认（81） | 固定灰系 | 5 圆 ±20° | 20 | 5 圆 | 110 | 同枪口 |
| ammoUseEffect | `Fx.none` | 默认（Turret.java:126，Scatter 未设） | — | 不生成 | — | 0 | — | — |

每扳机：2 发（shots=2，同点，间隔 5 tick）→ shoot+smoke 各 2 次（Turret.java:801-802 每 bullet() 一次）。子弹画色（非 FX）：frontColor/#fff8e8 bulletYellow、backColor/#f9c27a bulletYellowBack（BasicBulletType.java:13 默认，本弹未覆写）。**despawnHit 自动开**（splashDamageRadius>0，BulletType.java:831-835）：到寿先 flakExplosion（hit）再 hitBulletSmall（despawn）叠发。

### 2.3 Scatter — 玻璃弹（`FlakBulletType(4f, 3)`，Blocks.java:3362-3387）

| 特效槽 | Fx 名 | 来源 | 颜色 | 尺寸/形状 | lifetime | 数量 | layer | 生成点 |
|---|---|---|---|---|---|---|---|---|
| hitEffect | `Fx.flakExplosion` | 弹种显式（3373） | 固定三色（**e.color=#ffffff 被忽略**） | 同铅弹 | 20 | 5 圆 + 4 线 | 110 | 命中点 |
| despawnEffect | `Fx.hitBulletColor` | 弹种显式（3365） | 渐变 white→**#ffffff**（=Pal.glassAmmoFront，3364；纯白→纯白，视觉等同固定白） | 同 hitBulletColor（环+5 线+光晕 r=20，光晕白色） | 14 | 5 线 + 环 | 110 | 子弹当前位置 |
| shootEffect | `Fx.shootSmall` | 弹种显式（3369） | 固定橙系（吞 color=#ffffff） | 双三角 | 8 | 2 三角 | 110 | 枪口（shootY=8 默认） |
| smokeEffect | `Fx.shootSmallSmoke` | 默认（81） | 固定灰系 | 5 圆 | 20 | 5 圆 | 110 | 同枪口 |
| ammoUseEffect | `Fx.none` | 默认 | — | — | — | 0 | — | — |
| 破片（fragBullet, 3377-3386）despawnEffect | `Fx.none` | 显式（3384） | — | 不生成 | — | 0 | — | — |

每扳机：2 发（同铅弹，间隔 5 tick）→ shoot+smoke 各 2 次。破片弹：despawnEffect 显式关掉（3384）、hitEffect 默认 hitBulletSmall、命中即溅射（fragBullets=6）。子弹画色：front #ffffff（glassAmmoFront）、back #b9c9df（glassAmmoBack，3363）。

### 2.4 颜色来源汇总（"颜色从哪来"的答案）

- 特效的 color 参数 = `type.hitColor`（BulletType.java:550,644;Turret.java:801-802）——**每个弹种一个字段**，与子弹渲染色（BasicBulletType.frontColor/backColor）解耦：
  - 铜弹：`hitColor = backColor = #d39169`（front 是另一色 #eac1a8）——特效色 = **背衬色**，不是弹体主色。
  - 铅弹：`hitColor` 未覆写 = **白**（BulletType.java:241）。
  - 玻璃弹：`hitColor = frontColor = #ffffff`（back #b9c9df 不同）。
- 特效定义里**只有 `hitBulletColor` 消费 e.color**（渐变尾色 + 光晕，Fx.java:874,888）；`hitBulletSmall`/`flakExplosion`/`shootSmall`/`shootSmallSmoke`/`casing1` 全部固定调色板、无视 color 参数。所以"颜色数据"只有配上消费型特效才有可见差异。

## 3. 数据驱动 vs 硬编码评估

### 3.1 现状（MTurrets 侧证据）

- `BulletType`（`src/main/java/xyz/luobo/mturrets/core/combat/BulletType.kt:10-38`）只有 `color`（Int RGB）+ `bulletSize` 两个视觉字段，且 `color` 是**子弹渲染色**（贴图 tint，BulletRenderer.kt 读出 RGB 画 quad），没有 hit/despawn/shoot 任何特效字段；`BulletEntity` 同步的也只有 `DATA_COLOR`（顶字节打包 lifetime，ADR-0010）与 `DATA_SIZE`，命中在服务端直接 `discard()`（BulletEntity.kt:impact），客户端无命中/枪口粒子。
- ADR-0009 §后果已记录旧框架 `EffectType` 枚举到 MC 粒子端是"假接口"被否决——新骨架不该再做一个特效枚举假接口（docs/adr/0009-turret-domain-model.md:15）。
- MTurrets 现弹种（DuoTurretBE.kt:37-45、ScatterTurretBE.kt:43-74）：铜 #FFD37F、铅 #8C7FA9、玻璃 #EBEEF5——这些是 **Mindustry Items 配色**（ScatterTurretBE.kt:20 注释自称），不是上游特效/弹体色。

### 3.2 三弹种间实际有多少 FX 参数在变（依据第 2 节表）

| 槽位 | 铜（Duo） | 铅（Scatter） | 玻璃（Scatter） | 三弹种差异 |
|---|---|---|---|---|
| hitEffect 选型 | hitBulletColor | flakExplosion | flakExplosion | 2 个不同特效 |
| hitEffect 有效颜色 | #d39169 | （固定调色板，不吃 color） | （固定调色板，不吃 color） | 1 处有颜色输入 |
| despawnEffect 选型 | hitBulletColor | hitBulletSmall | hitBulletColor | 2 个特效 |
| despawnEffect 有效颜色 | #d39169 | （固定调色板） | #ffffff（=纯白，无色差） | 1 处有可见颜色输入 |
| shootEffect | shootSmall | shootSmall | shootSmall | **0 差异**（含颜色） |
| smokeEffect | shootSmallSmoke | shootSmallSmoke | shootSmallSmoke | **0 差异** |
| ammoUseEffect | casing1 | none | none | 按炮台（Duo only），非按弹种 |

- 差异的真实规模：**7 个槽位中命中 + 消失 4 个槽位有差异**（2 种特效选型 × 相关颜色组合），枪口 3 个槽位零差异；特效内部 lifetime/数量/尺寸全部固定、不随弹种变。
- 若硬编码"每弹种调色板"进渲染器：需要按弹种键的 3 组渲染参数（选型 + 颜色）——**本质上就是一张查找表**，只是放在渲染器里、以弹药名为键，和放在 BulletType 数据里同量级的数据、位置更差。
- 颜色字段最小集：上游一个 `hitColor` 同时喂 hit/despawn/shoot/smoke 四个槽位（BulletType.java:550,644;Turret.java:801-802）；MTurrets 现有 `color` 是 front 弹体色，与上游 hitColor 语义**恰好错位**（铜弹上游 hitColor = back 色 #d39169 ≠ front #eac1a8；见 2.4），不能直接拿 `color` 当特效色。

### 3.3 建议

**推荐：数据驱动字段**——`BulletType` 增加 `hitEffect`/`despawnEffect`（命名特效 + 颜色参数）与一个 `hitColor` 颜色字段（上游即单字段共喂四个槽位）；枪口 shoot/smoke 因三弹种完全一致可做渲染器侧单一模板（共享常量，不是"假接口"）。依据：命中/消失 4/6 个槽位在 3 弹种间确实选型和颜色都不同（硬编码 = 同数据放错地方，且 MTurrets 现有 `color` 与上游 hitColor 语义错位、无法复用）；枪口 3 槽零差异、玻璃弹 despawn 颜色输入为纯白无可见性，这两处是唯一能省的数据。最终取舍（是否加字段、字段形状、MC 粒子实现）留给实现票决策，本文件不定案。

## 未证实项 / 术语澄清

- `randLenVectors` / `Drawf.tri` / `Drawf.light` / `Fill` / `Lines` 的 arc 实现不在稀疏检出内（`ref/mindustry/core/src/arc/` 不存在，arc 是 gradle 依赖）：粒子向量分布（是否 360° 均匀）与光晕绘制细节未直接引用源码，仅从调用参数（count/length/角度+散布）描述。上游库行为【未证实】。
- `Effect.EffectContainer.scaled()` 的分段渲染时序（主渲染体与 scaled 子渲染是否并存）未读 arc 实现，仅按 Fx.java 调用形态描述【未证实；不影响参数表结论】。
- 相机 clip=50 与 `enableEffects` 门控在 headless/服务器不生成特效（Effect.java:141-148 已证实）；"特效的客户端网络同步方式"与 MTurrets 无关，未追。
- 破片弹（metaglass fragBullet）命中特效：Blocks.java 未覆写 → 默认 hitBulletSmall；其自身无 splashDamage 故 despawnHit 保持 false【推导，非逐行追读】。
- 术语：`hitColor` 上游注释为 "Color used for hit/despawn effects"（BulletType.java:240），实际也传给 shoot/smoke（Turret.java:801-802）——注释与行为不一致，以行为为准。
- Scatter 铅/玻璃弹 despawn 双特效叠发顺序（hit 在 despawn 前，BulletType.java:638-644）已证实；肉眼合并观感未做渲染对照。

## 坐标速查（本文件引用文件）

- `ref/mindustry/core/src/mindustry/entities/bullet/{BulletType,BasicBulletType,FlakBulletType}.java`
- `ref/mindustry/core/src/mindustry/content/{Fx,Blocks}.java`
- `ref/mindustry/core/src/mindustry/world/blocks/defense/turrets/Turret.java`
- `ref/mindustry/core/src/mindustry/entities/pattern/{ShootPattern,ShootAlternate}.java`
- `ref/mindustry/core/src/mindustry/entities/{Effect,comp/BulletComp,comp/TimedComp}.java`
- `ref/mindustry/core/src/mindustry/graphics/{Pal,Layer}.java`、`ref/mindustry/core/src/mindustry/Vars.java`
- `src/main/java/xyz/luobo/mturrets/core/combat/BulletType.kt`、`src/main/java/xyz/luobo/mturrets/client/renderers/BulletRenderer.kt`、`src/main/java/xyz/luobo/mturrets/common/entity/bullet/BulletEntity.kt`、`src/main/java/xyz/luobo/mturrets/common/turrets/{DuoTurretBE,ScatterTurretBE}.kt`
- `docs/adr/0009-turret-domain-model.md`
