package xyz.luobo.mindustry.core.turret.ammo

/**
 * 追踪统计
 * 定义弹药的追踪能力
 */
data class HomingStats(
    /** 转向速度（角度/秒） */
    val turnSpeed: Float,
    /** 追踪范围（格） */
    val trackingRange: Float
) {
    companion object {
        /** 无追踪能力 */
        val NONE = HomingStats(0f, 0f)
    }

    /** 是否具有追踪能力 */
    val hasTracking: Boolean
        get() = turnSpeed > 0f && trackingRange > 0f
}