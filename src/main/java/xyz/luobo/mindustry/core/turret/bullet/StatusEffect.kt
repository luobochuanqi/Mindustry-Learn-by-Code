package xyz.luobo.mindustry.core.turret.bullet

/**
 * 状态效果类型
 * 定义攻击可以施加的各种状态效果
 */
sealed class StatusEffect(
    val name: String,
    val description: String
) {
    /**
     * 燃烧效果
     * 持续造成伤害
     */
    object BURNING : StatusEffect(
        "burning",
        "持续燃烧伤害"
    )

    /**
     * 冰冻效果
     * 降低移动速度
     */
    object FREEZING : StatusEffect(
        "freezing",
        "降低移动速度"
    )

    /**
     * 中毒效果
     * 持续造成伤害（可叠加）
     */
    object POISONED : StatusEffect(
        "poisoned",
        "持续中毒伤害"
    )

    /**
     * 虚弱效果
     * 降低攻击力
     */
    object WEAKENED : StatusEffect(
        "weakened",
        "降低攻击力"
    )

    /**
     * 致盲效果
     * 降低视野或攻击精度
     */
    object BLINDED : StatusEffect(
        "blinded",
        "降低视野"
    )

    /**
     * 缓慢效果
     * 降低移动和攻击速度
     */
    object SLOWED : StatusEffect(
        "slowed",
        "降低速度"
    )

    /**
     * 眩晕效果
     * 短暂无法行动
     */
    object STUNNED : StatusEffect(
        "stunned",
        "无法行动"
    )

    /**
     * 腐蚀效果
     * 降低护甲
     */
    object CORRODED : StatusEffect(
        "corroded",
        "降低护甲"
    )

    /**
     * 电击效果
     * 短暂麻痹
     */
    object ELECTRIFIED : StatusEffect(
        "electrified",
        "电击麻痹"
    )

    /**
     * 湿润效果
     * 增加雷电伤害，降低火焰伤害
     */
    object WET : StatusEffect(
        "wet",
        "湿润状态"
    )

    /**
     * 油滑效果
     * 增加火焰伤害
     */
    object OILY : StatusEffect(
        "oily",
        "油滑状态"
    )

    /**
     *  tar 覆盖效果
     * 大幅降低移动速度
     */
    object TARRED : StatusEffect(
        "tarred",
        "粘稠覆盖"
    )

    companion object {
        /**
         * 通过名称获取状态效果
         */
        fun byName(name: String): StatusEffect? {
            return when (name.lowercase()) {
                "burning" -> BURNING
                "freezing" -> FREEZING
                "poisoned" -> POISONED
                "weakened" -> WEAKENED
                "blinded" -> BLINDED
                "slowed" -> SLOWED
                "stunned" -> STUNNED
                "corroded" -> CORRODED
                "electrified" -> ELECTRIFIED
                "wet" -> WET
                "oily" -> OILY
                "tarred" -> TARRED
                else -> null
            }
        }
    }
}

/**
 * 状态效果实例
 * 包含效果类型、持续时间和强度
 */
data class StatusEffectInstance(
    val effect: StatusEffect,
    val duration: Int,  // tick
    val strength: Int = 0
) {
    /**
     * 检查效果是否有效
     */
    val isValid: Boolean
        get() = duration > 0

    /**
     * 减少持续时间
     */
    fun tick(): StatusEffectInstance {
        return if (duration > 0) {
            copy(duration = duration - 1)
        } else {
            this
        }
    }

    companion object {
        /**
         * 创建燃烧效果实例（快捷方法）
         */
        fun burning(duration: Int, damagePerTick: Float = 1f) =
            StatusEffectInstance(StatusEffect.BURNING, duration, damagePerTick.toInt())

        /**
         * 创建冰冻效果实例（快捷方法）
         */
        fun freezing(duration: Int, slowdownPercent: Float = 0.5f) =
            StatusEffectInstance(StatusEffect.FREEZING, duration, (slowdownPercent * 100).toInt())

        /**
         * 创建中毒效果实例（快捷方法）
         */
        fun poisoned(duration: Int, damagePerTick: Float = 0.5f) =
            StatusEffectInstance(StatusEffect.POISONED, duration, damagePerTick.toInt())

        /**
         * 创建缓慢效果实例（快捷方法）
         */
        fun slowed(duration: Int, slowdownPercent: Float = 0.3f) =
            StatusEffectInstance(StatusEffect.SLOWED, duration, (slowdownPercent * 100).toInt())
    }
}