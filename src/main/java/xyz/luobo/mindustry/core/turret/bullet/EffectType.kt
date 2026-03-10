package xyz.luobo.mindustry.core.turret.bullet

/**
 * 视觉效果类型
 * 定义各种射击、击中、爆炸等视觉效果
 */
sealed class EffectType(
    val name: String,
    val particleCount: Int = 0,
    val soundVolume: Float = 1.0f,
    val soundPitch: Float = 1.0f
) {
    // ========== 无效果 ==========
    object NONE : EffectType("none", 0)

    // ========== 射击效果 ==========
    object SMALL_SHOOT : EffectType(
        "small_shoot",
        particleCount = 5,
        soundVolume = 0.8f,
        soundPitch = 1.0f
    )

    object MEDIUM_SHOOT : EffectType(
        "medium_shoot",
        particleCount = 10,
        soundVolume = 1.0f,
        soundPitch = 1.0f
    )

    object LARGE_SHOOT : EffectType(
        "large_shoot",
        particleCount = 20,
        soundVolume = 1.2f,
        soundPitch = 0.9f
    )

    object LASER_SHOOT : EffectType(
        "laser_shoot",
        particleCount = 15,
        soundVolume = 0.6f,
        soundPitch = 1.2f
    )

    object BEAM_SHOOT : EffectType(
        "beam_shoot",
        particleCount = 8,
        soundVolume = 0.7f,
        soundPitch = 0.8f
    )

    // ========== 击中效果 ==========
    object SMALL_HIT : EffectType(
        "small_hit",
        particleCount = 5,
        soundVolume = 0.5f,
        soundPitch = 1.0f
    )

    object MEDIUM_HIT : EffectType(
        "medium_hit",
        particleCount = 10,
        soundVolume = 0.7f,
        soundPitch = 1.0f
    )

    object LARGE_HIT : EffectType(
        "large_hit",
        particleCount = 20,
        soundVolume = 0.9f,
        soundPitch = 0.9f
    )

    object LASER_HIT : EffectType(
        "laser_hit",
        particleCount = 10,
        soundVolume = 0.4f,
        soundPitch = 1.1f
    )

    // ========== 爆炸效果 ==========
    object SMALL_EXPLOSION : EffectType(
        "small_explosion",
        particleCount = 15,
        soundVolume = 1.0f,
        soundPitch = 1.0f
    )

    object MEDIUM_EXPLOSION : EffectType(
        "medium_explosion",
        particleCount = 30,
        soundVolume = 1.2f,
        soundPitch = 0.9f
    )

    object LARGE_EXPLOSION : EffectType(
        "large_explosion",
        particleCount = 50,
        soundVolume = 1.5f,
        soundPitch = 0.8f
    )

    object NUCLEAR_EXPLOSION : EffectType(
        "nuclear_explosion",
        particleCount = 100,
        soundVolume = 2.0f,
        soundPitch = 0.6f
    )

    // ========== 特殊效果 ==========
    object ELECTRIC_SPARK : EffectType(
        "electric_spark",
        particleCount = 8,
        soundVolume = 0.6f,
        soundPitch = 1.3f
    )

    object FIRE_BURST : EffectType(
        "fire_burst",
        particleCount = 12,
        soundVolume = 0.7f,
        soundPitch = 0.9f
    )

    object ICE_SHATTER : EffectType(
        "ice_shatter",
        particleCount = 10,
        soundVolume = 0.5f,
        soundPitch = 1.4f
    )

    object POISON_CLOUD : EffectType(
        "poison_cloud",
        particleCount = 15,
        soundVolume = 0.4f,
        soundPitch = 0.7f
    )

    object ACID_SPLASH : EffectType(
        "acid_splash",
        particleCount = 10,
        soundVolume = 0.5f,
        soundPitch = 0.8f
    )

    // ========== 拖尾效果 ==========
    object SMALL_TRAIL : EffectType("small_trail", 3)
    object MEDIUM_TRAIL : EffectType("medium_trail", 5)
    object LARGE_TRAIL : EffectType("large_trail", 8)
    object FIRE_TRAIL : EffectType("fire_trail", 6)
    object SMOKE_TRAIL : EffectType("smoke_trail", 4)
    object ENERGY_TRAIL : EffectType("energy_trail", 7)

    // ========== 烟雾效果 ==========
    object SMALL_SMOKE : EffectType("small_smoke", 5)
    object MEDIUM_SMOKE : EffectType("medium_smoke", 10)
    object LARGE_SMOKE : EffectType("large_smoke", 15)
    object BLACK_SMOKE : EffectType("black_smoke", 12)
    object STEAM : EffectType("steam", 8)

    // ========== 消失效果 ==========
    object SMALL_DESPAWN : EffectType("small_despawn", 3)
    object MEDIUM_DESPAWN : EffectType("medium_despawn", 6)
    object LARGE_DESPAWN : EffectType("large_despawn", 10)

    companion object {
        /**
         * 通过名称获取效果类型
         */
        fun byName(name: String): EffectType? {
            return when (name.lowercase()) {
                "none" -> NONE
                "small_shoot" -> SMALL_SHOOT
                "medium_shoot" -> MEDIUM_SHOOT
                "large_shoot" -> LARGE_SHOOT
                "laser_shoot" -> LASER_SHOOT
                "beam_shoot" -> BEAM_SHOOT
                "small_hit" -> SMALL_HIT
                "medium_hit" -> MEDIUM_HIT
                "large_hit" -> LARGE_HIT
                "laser_hit" -> LASER_HIT
                "small_explosion" -> SMALL_EXPLOSION
                "medium_explosion" -> MEDIUM_EXPLOSION
                "large_explosion" -> LARGE_EXPLOSION
                "nuclear_explosion" -> NUCLEAR_EXPLOSION
                "electric_spark" -> ELECTRIC_SPARK
                "fire_burst" -> FIRE_BURST
                "ice_shatter" -> ICE_SHATTER
                "poison_cloud" -> POISON_CLOUD
                "acid_splash" -> ACID_SPLASH
                "small_trail" -> SMALL_TRAIL
                "medium_trail" -> MEDIUM_TRAIL
                "large_trail" -> LARGE_TRAIL
                "fire_trail" -> FIRE_TRAIL
                "smoke_trail" -> SMOKE_TRAIL
                "energy_trail" -> ENERGY_TRAIL
                "small_smoke" -> SMALL_SMOKE
                "medium_smoke" -> MEDIUM_SMOKE
                "large_smoke" -> LARGE_SMOKE
                "black_smoke" -> BLACK_SMOKE
                "steam" -> STEAM
                "small_despawn" -> SMALL_DESPAWN
                "medium_despawn" -> MEDIUM_DESPAWN
                "large_despawn" -> LARGE_DESPAWN
                else -> null
            }
        }

        /**
         * 根据伤害值自动选择适当的击中效果
         */
        fun autoHitEffect(damage: Float): EffectType {
            return when {
                damage < 5f -> SMALL_HIT
                damage < 15f -> MEDIUM_HIT
                damage < 30f -> LARGE_HIT
                else -> MEDIUM_EXPLOSION
            }
        }

        /**
         * 根据范围伤害自动选择适当的爆炸效果
         */
        fun autoExplosionEffect(splashDamage: Float, splashRadius: Float): EffectType {
            val intensity = splashDamage * splashRadius
            return when {
                intensity < 20f -> SMALL_EXPLOSION
                intensity < 60f -> MEDIUM_EXPLOSION
                intensity < 150f -> LARGE_EXPLOSION
                else -> NUCLEAR_EXPLOSION
            }
        }
    }
}