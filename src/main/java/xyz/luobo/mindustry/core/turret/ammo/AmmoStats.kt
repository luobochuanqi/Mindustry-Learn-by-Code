package xyz.luobo.mindustry.core.turret.ammo

import net.minecraft.world.item.Item

/**
 * 弹药统计
 * 定义特定弹药在炮台中的属性
 *
 * 支持多种属性组合：
 * - 基础伤害 + 装填倍数 + 射程加成 + 射速倍率
 * - 范围伤害（可选）
 * - 追踪能力（可选）
 * - 弹药效果（可选）
 *
 * 属性可以自由组合，例如：
 * - 只有基础伤害：使用 basic()
 * - 范围伤害：使用 splash()
 * - 追踪能力：使用 homing()
 * - 追踪 + 范围伤害：使用 homingSplash() 或 explosiveMissile()
 * - 完全自定义：使用 advanced()
 */
data class AmmoStats(
    /** 物品 */
    val item: Item,

    /** 基础伤害 */
    val damage: Float,

    /** 装填倍数（影响开火速率） */
    val reloadMultiplier: Float = 1.0f,

    /** 射程加成（格） */
    val rangeBonus: Float = 0f,

    /** 射速倍率 */
    val fireRateMultiplier: Float = 1.0f,

    /** 范围伤害统计 */
    val splashStats: SplashStats = SplashStats.NONE,

    /** 追踪统计 */
    val homingStats: HomingStats = HomingStats.NONE,

    /** 弹药效果列表 */
    val effects: List<AmmoEffect> = emptyList()
) {
    /** 是否具有范围伤害 */
    val hasSplash: Boolean
        get() = splashStats.hasSplash

    /** 是否具有追踪能力 */
    val hasTracking: Boolean
        get() = homingStats.hasTracking

    /** 是否有任何效果 */
    val hasEffects: Boolean
        get() = effects.isNotEmpty()

    /** 是否同时具有追踪和范围伤害 */
    val hasHomingAndSplash: Boolean
        get() = hasSplash && hasTracking

    /** 总伤害（基础伤害 + 范围伤害） */
    val totalDamage: Float
        get() = damage + if (hasSplash) splashStats.splashDamage else 0f

    companion object {
        /**
         * 创建基础弹药
         * 只有基础伤害，无特殊效果
         */
        fun basic(
            item: Item,
            damage: Float,
            reloadMultiplier: Float = 1.0f,
            rangeBonus: Float = 0f,
            fireRateMultiplier: Float = 1.0f
        ) = AmmoStats(
            item = item,
            damage = damage,
            reloadMultiplier = reloadMultiplier,
            rangeBonus = rangeBonus,
            fireRateMultiplier = fireRateMultiplier
        )

        /**
         * 创建范围伤害弹药
         * 命中目标后造成爆炸范围伤害
         * @param splashDamage 范围伤害值
         * @param splashRadius 爆炸半径（格）
         */
        fun splash(
            item: Item,
            damage: Float,
            splashDamage: Float,
            splashRadius: Float,
            reloadMultiplier: Float = 1.0f,
            rangeBonus: Float = 0f
        ) = AmmoStats(
            item = item,
            damage = damage,
            reloadMultiplier = reloadMultiplier,
            rangeBonus = rangeBonus,
            splashStats = SplashStats(splashDamage, splashRadius)
        )

        /**
         * 创建追踪弹药
         * 会追踪目标直到命中或超时
         * @param turnSpeed 转向速度（角度/秒）
         * @param trackingRange 追踪范围（格）
         */
        fun homing(
            item: Item,
            damage: Float,
            turnSpeed: Float,
            trackingRange: Float,
            reloadMultiplier: Float = 1.0f,
            rangeBonus: Float = 0f
        ) = AmmoStats(
            item = item,
            damage = damage,
            reloadMultiplier = reloadMultiplier,
            rangeBonus = rangeBonus,
            homingStats = HomingStats(turnSpeed, trackingRange)
        )

        /**
         * 创建带效果的弹药
         * 可以添加燃烧、冰冻、剧毒等效果
         * @param effects 效果列表
         */
        fun withEffects(
            item: Item,
            damage: Float,
            effects: List<AmmoEffect>,
            reloadMultiplier: Float = 1.0f,
            rangeBonus: Float = 0f
        ) = AmmoStats(
            item = item,
            damage = damage,
            reloadMultiplier = reloadMultiplier,
            rangeBonus = rangeBonus,
            effects = effects
        )

        /**
         * 创建同时具有追踪和范围伤害的弹药
         * @param item 物品
         * @param damage 基础伤害
         * @param splashDamage 范围伤害值
         * @param splashRadius 爆炸半径（格）
         * @param turnSpeed 转向速度（角度/秒）
         * @param trackingRange 追踪范围（格）
         * @param reloadMultiplier 装填倍数
         * @param rangeBonus 射程加成
         */
        fun homingSplash(
            item: Item,
            damage: Float,
            splashDamage: Float,
            splashRadius: Float,
            turnSpeed: Float,
            trackingRange: Float,
            reloadMultiplier: Float = 1.0f,
            rangeBonus: Float = 0f
        ) = AmmoStats(
            item = item,
            damage = damage,
            reloadMultiplier = reloadMultiplier,
            rangeBonus = rangeBonus,
            splashStats = SplashStats(splashDamage, splashRadius),
            homingStats = HomingStats(turnSpeed, trackingRange)
        )

        /**
         * 创建追踪范围伤害弹药（带效果的简化版本）
         * @param item 物品
         * @param damage 基础伤害
         * @param splashStats 范围伤害统计
         * @param homingStats 追踪统计
         * @param effects 弹药效果列表
         */
        fun advanced(
            item: Item,
            damage: Float,
            splashStats: SplashStats = SplashStats.NONE,
            homingStats: HomingStats = HomingStats.NONE,
            effects: List<AmmoEffect> = emptyList(),
            reloadMultiplier: Float = 1.0f,
            rangeBonus: Float = 0f,
            fireRateMultiplier: Float = 1.0f
        ) = AmmoStats(
            item = item,
            damage = damage,
            reloadMultiplier = reloadMultiplier,
            rangeBonus = rangeBonus,
            fireRateMultiplier = fireRateMultiplier,
            splashStats = splashStats,
            homingStats = homingStats,
            effects = effects
        )
    }
}