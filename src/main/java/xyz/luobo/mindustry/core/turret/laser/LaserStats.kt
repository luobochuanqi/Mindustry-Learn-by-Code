package xyz.luobo.mindustry.core.turret.laser

/**
 * 激光统计
 * 定义激光炮台的属性
 */
data class LaserStats(
    /** 每秒伤害 */
    val damagePerSecond: Float,

    /** 激光颜色（RGB，0-255） */
    val color: Int = 0xFF0000,

    /** 激光宽度 */
    val width: Float = 0.5f,

    /** 是否有穿透效果 */
    val pierce: Boolean = false,

    /** 穿透数量 */
    val pierceCount: Int = 0
) {
    companion object {
        /** 无激光 */
        val NONE = LaserStats(0f)

        /**
         * 创建基础激光
         */
        fun basic(damagePerSecond: Float, color: Int = 0xFF0000) = LaserStats(
            damagePerSecond = damagePerSecond,
            color = color
        )

        /**
         * 创建穿透激光
         */
        fun piercing(damagePerSecond: Float, pierceCount: Int, color: Int = 0x00FF00) = LaserStats(
            damagePerSecond = damagePerSecond,
            color = color,
            pierce = true,
            pierceCount = pierceCount
        )
    }

    /** 是否启用激光 */
    val isEnabled: Boolean
        get() = damagePerSecond > 0f

    /** 激光颜色分量 */
    val red: Int get() = (color shr 16) and 0xFF
    val green: Int get() = (color shr 8) and 0xFF
    val blue: Int get() = color and 0xFF
}