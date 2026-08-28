# ADR-0006: 弹道与工艺通道——单 Bullet 实体 + 配置数据对象；配方抄 Create 骨架加 energy

状态：accepted（2026-08 敲定）

**弹道**：对位 Mindustry 架构（单一池化 Bullet 实体 + BulletType 配置对象承载全部家族；光束/闪电是 speed=0 的"子弹"在 init 解伤害）。MC 映射为：飞行弹共用一个通用 `BulletEntity`，行为差异（速度/重力近似/追踪/曳光/分裂）全在 `BulletType` JSON 数据对象；瞬时类（Arc 闪电链、Lancer 激光、Meltdown 持续光束）服务端射线判定、零实体，客户端靠计数器字段播视觉。一期只做飞行弹家族（Duo/钍类），瞬时家族实现时按本原则接入。

**工艺**：抄 Create 1.21.1 配方骨架——RecipeType/Serializer 代码注册，配方实例为纯 datapack JSON（`{ingredients, results, processing_time}` 单 codec 家族，概率产出照 `rollOutput` 语义），配方查找事件驱动 + 缓存（禁 per-tick getRecipeFor），每 tick 只减进度。与 Create 的唯一字段差异：加 `energy`（每配方功耗——Mindustry 机器费电是配方级语义，Create 的动力消耗在方块不在配方）。数值表（炮台属性/弹表/蓝图）与配方的分工：形状与默认数值在 Kotlin 代码表，可重平衡的数值/配方进 datapack JSON。

## Considered Options

- 子弹全实体化（光束=跟随炮口的持久实体）：否决——同步开销大且转向拖尾。
- 一期即数据化弹道（BE 数据 + 客户端视觉）：否决——自写碰撞判定是平行宇宙，原版实体免费拿区块/碰撞/存档；量大了再升级渲染通道。
- 复用原版熔炉框架：否决——多入多出+功耗的形状迟早撞墙。
