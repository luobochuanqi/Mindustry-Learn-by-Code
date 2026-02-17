package xyz.luobo.mindustry.core.turret.ammo

/**
 * 范围伤害统计
 * 定义弹药的范围伤害能力
 */
data class SplashStats(
    /** 范围伤害值 */
    val splashDamage: Float,
    /** 爆炸半径（格） */
    val splashRadius: Float
) {
    companion object {
        /** 无范围伤害 */
        val NONE = SplashStats(0f, 0f)
    }

    /** 是否具有范围伤害 */
    val hasSplash: Boolean
        get() = splashDamage > 0f && splashRadius > 0f
}