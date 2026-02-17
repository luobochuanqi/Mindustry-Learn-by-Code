package xyz.luobo.mindustry.core.turret.liquid

import net.minecraft.world.level.material.Fluid

/**
 * 炮台液体配置
 * 定义特定液体对炮台的影响
 */
data class TurretLiquid(
    /** 液体 */
    val fluid: Fluid,

    /** 输入速率（每 tick 输入量） */
    val inputRate: Int,

    /** 开火速率乘百分比（例如：0.2 表示增加 20% 开火速率） */
    val fireRateMultiplier: Float = 0f
) {
    /** 是否影响开火速率 */
    val affectsFireRate: Boolean
        get() = fireRateMultiplier != 0f

    companion object {
        /**
         * 创建仅用于强化的液体
         */
        fun forBoost(
            fluid: Fluid,
            inputRate: Int,
            fireRateMultiplier: Float
        ) = TurretLiquid(fluid, inputRate, fireRateMultiplier)
    }
}