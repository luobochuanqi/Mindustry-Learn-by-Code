package xyz.luobo.mturrets.core.combat

/**
 * 一种弹药的静态定义(ADR-0006/0009):行为差异全在数据对象,全部弹种共用一个 [BulletEntity]。
 * 数值分工(ADR-0006):形状与默认数值在 Kotlin 代码表(本类 + [TurretSpec]),可重平衡通道二期再评估。
 *
 * 一期只做飞行弹家族,字段最小化:穿透/分裂/曳光/追踪/范围伤害等高级行为留位不实现、不预埋字段
 * (#31 spec 决议);gravity 默认 0(直射),>0 时每 tick 施加重力近似。
 */
data class BulletType(
    /** 单发伤害 */
    val damage: Float,
    /** 弹速(格/tick) */
    val speed: Float,
    /** 存活时间(tick),超时即消失 */
    val lifetime: Int,
    /** RGB 颜色(客户端渲染,同步字段) */
    val color: Int = 0xFFFFFF,
    /** 视觉大小(同步字段,渲染放大倍率) */
    val bulletSize: Float = 0.5f,
    /** 重力影响(0 无重力;>0 每 tick 速度 -0.04×gravity) */
    val gravity: Float = 0f,
    /** 出生散布(度,与炮台自身 inaccuracy 叠加) */
    val inaccuracy: Float = 0f
)