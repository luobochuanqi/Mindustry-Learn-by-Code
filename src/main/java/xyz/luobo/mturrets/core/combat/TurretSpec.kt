package xyz.luobo.mturrets.core.combat

import net.minecraft.world.item.Item

/**
 * 炮台静态数值表(ADR-0009):形状与默认数值在 Kotlin 代码表,弹种差异全进数据。
 * 与 [BulletType] 分离:炮台管装填/索敌/旋转,子弹管弹道/伤害。
 */
data class TurretSpec(
    /** 射程(格) */
    val range: Float,
    /** 装填时长(tick) */
    val reloadTicks: Float,
    /** 每扳机出膛数(点射) */
    val shots: Int,
    /** 点射间隔(tick;0 = 同刻齐出) */
    val shotDelay: Float,
    /** 炮台自身散布(度) */
    val inaccuracy: Float,
    /** 开火锥角(度):枪口转向目标角小于该值方可开火 */
    val shootCone: Float,
    /** 旋转速度(度/tick) */
    val rotateSpeed: Float,
    /** 弹仓容量(按弹药单位计,ADR-0009) */
    val maxAmmo: Int,
    /** 结构 Health(锚点单条,ADR-0003) */
    val health: Int,
    /** Coolant 装填倍率(桶灌水场景,ADR-0009;#28 决议 ×1.5) */
    val coolantReloadMultiplier: Float,
    /** 每发耗水(mB,开火记账) */
    val coolantPerShot: Int,
    /** 结构跨度(格):2×2 = 2;发射/瞄准/旋转中心 = 锚点中心 + (size-1)/2 每水平轴(结构中心,#34)。 */
    val size: Int = 1,
    /** 对空索敌:悬空(!onGround)的 Monster。 */
    val targetAir: Boolean = true,
    /** 对地索敌:落地(onGround)的 Monster。 */
    val targetGround: Boolean = true,
    /** 弹药表:物品 → 弹定义 + 入仓倍率 */
    val ammoTypes: List<AmmoType>
)

/** 一种弹药物品:入仓 1 物品折算 [unitMultiplier] 单位;每扳机扣 1 单位。 */
data class AmmoType(
    val item: Item,
    val bullet: BulletType,
    /** 物品 → 弹药单位换算(入仓乘、拆机除,ADR-0009) */
    val unitMultiplier: Int
)