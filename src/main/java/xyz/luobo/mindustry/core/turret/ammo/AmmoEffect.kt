package xyz.luobo.mindustry.core.turret.ammo

/**
 * 弹药效果
 * 占位符，用于定义弹药的附加效果
 */
sealed interface AmmoEffect {
    val effectName: String
}

/**
 * 燃烧效果
 */
data class BurnEffect(
    val duration: Int,
    val damagePerTick: Float
) : AmmoEffect {
    override val effectName: String = "burn"
}

/**
 * 冰冻效果
 */
data class FreezeEffect(
    val duration: Int,
    val slowdown: Float
) : AmmoEffect {
    override val effectName: String = "freeze"
}

/**
 * 剧毒效果
 */
data class PoisonEffect(
    val duration: Int,
    val damagePerTick: Float
) : AmmoEffect {
    override val effectName: String = "poison"
}

/**
 * 爆炸效果
 */
data class ExplosiveEffect(
    val damage: Float,
    val radius: Float
) : AmmoEffect {
    override val effectName: String = "explosive"
}