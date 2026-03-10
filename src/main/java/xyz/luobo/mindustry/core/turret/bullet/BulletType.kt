package xyz.luobo.mindustry.core.turret.bullet

import net.minecraft.world.phys.Vec3

/**
 * 子弹/攻击类型
 * 统一定义所有攻击方式的属性
 * 模仿 Mindustry 的 BulletType 设计
 *
 * @property damage 基础伤害（单次或每秒，取决于 continuous）
 * @property speed 弹丸速度（0 表示瞬间命中，如激光）
 * @property rangeOverride 射程覆盖（-1 表示使用炮台默认射程）
 * @property lifetime 弹丸存活时间（tick）
 * @property color 子弹颜色（RGB）
 * @property bulletSize 子弹大小
 * @property trailLength 拖尾长度
 * @property splashDamage 范围伤害值（0 表示无范围伤害）
 * @property splashRadius 范围伤害半径
 * @property splashDamageAir 范围伤害是否对空
 * @property splashDamageGround 范围伤害是否对地
 * @property pierce 是否穿透
 * @property pierceCap 穿透数量（-1 表示无限）
 * @property pierceDamageFactor 穿透后伤害衰减
 * @property homing 是否追踪目标
 * @property homingPower 追踪强度
 * @property homingRange 追踪范围
 * @property statusEffects 状态效果列表
 * @property statusDuration 状态效果持续时间
 * @property instant 是否立即命中（激光/光束）
 * @property continuous 是否持续伤害（激光）
 * @property laserWidth 激光宽度
 * @property healPercent 治疗百分比（负数为伤害）
 * @property gravity 重力影响
 * @property bounce 弹力（0-1）
 * @property collidesWall 是否碰撞墙壁
 * @property collidesAir 是否对空
 * @property collidesGround 是否对地
 * @property despawnOnHit 击中时是否消失
 * @property shootEffect 射击效果
 * @property hitEffect 击中效果
 * @property smokeEffect 烟雾效果
 * @property trailEffect 拖尾效果
 * @property despawnEffect 消失效果
 */
data class BulletType(
    // ========== 基础属性 ==========
    val damage: Float = 0f,
    val speed: Float = 1f,
    val rangeOverride: Float = -1f,
    val lifetime: Int = 60,

    // ========== 外观属性 ==========
    val color: Int = 0xFFFFFF,
    val bulletSize: Float = 1f,
    val trailLength: Float = 0f,

    // ========== 范围伤害 ==========
    val splashDamage: Float = 0f,
    val splashRadius: Float = 0f,
    val splashDamageAir: Boolean = true,
    val splashDamageGround: Boolean = true,

    // ========== 穿透属性 ==========
    val pierce: Boolean = false,
    val pierceCap: Int = -1,
    val pierceDamageFactor: Float = 1f,

    // ========== 追踪属性 ==========
    val homing: Boolean = false,
    val homingPower: Float = 0f,
    val homingRange: Float = 0f,

    // ========== 状态效果 ==========
    val statusEffects: List<StatusEffectInstance> = emptyList(),
    val statusDuration: Float = 0f,

    // ========== 特殊属性 ==========
    val instant: Boolean = false,
    val continuous: Boolean = false,
    val laserWidth: Float = 0.5f,
    val healPercent: Float = 0f,

    // ========== 物理属性 ==========
    val gravity: Float = 0f,
    val bounce: Float = 0f,
    val collidesWall: Boolean = true,
    val collidesAir: Boolean = true,
    val collidesGround: Boolean = true,
    val despawnOnHit: Boolean = true,

    // ========== 视觉效果 ==========
    val shootEffect: EffectType = EffectType.NONE,
    val hitEffect: EffectType = EffectType.NONE,
    val smokeEffect: EffectType = EffectType.NONE,
    val trailEffect: EffectType = EffectType.NONE,
    val despawnEffect: EffectType = EffectType.NONE
) {
    // ========== 计算属性 ==========

    /**
     * 是否具有范围伤害
     */
    val hasSplash: Boolean
        get() = splashDamage > 0f && splashRadius > 0f

    /**
     * 是否具有追踪能力
     */
    val hasHoming: Boolean
        get() = homing && homingPower > 0f && homingRange > 0f

    /**
     * 是否有状态效果
     */
    val hasStatusEffects: Boolean
        get() = statusEffects.isNotEmpty()

    /**
     * 是否是激光类型（持续 + 瞬间）
     */
    val isLaser: Boolean
        get() = instant && continuous

    /**
     * 是否是光束类型（瞬间但不持续）
     */
    val isBeam: Boolean
        get() = instant && !continuous

    /**
     * 是否有拖尾效果
     */
    val hasTrail: Boolean
        get() = trailLength > 0f

    /**
     * 总伤害（基础伤害 + 范围伤害）
     */
    val totalDamage: Float
        get() = damage + if (hasSplash) splashDamage else 0f

    /**
     * 激光颜色分量
     */
    val red: Int get() = (color shr 16) and 0xFF
    val green: Int get() = (color shr 8) and 0xFF
    val blue: Int get() = color and 0xFF

    /**
     * 激光颜色分量（0-1 范围）
     */
    val redF: Float get() = red / 255f
    val greenF: Float get() = green / 255f
    val blueF: Float get() = blue / 255f

    // ========== 方法 ==========

    /**
     * 计算实际射程
     * @param baseRange 炮台基础射程
     * @return 实际射程
     */
    fun calculateRange(baseRange: Float): Float {
        return if (rangeOverride > 0f) rangeOverride else baseRange
    }

    /**
     * 计算实际速度（考虑误差）
     * @param inaccuracy 误差角度（度）
     * @param baseDirection 基础方向向量
     * @return 带误差的速度向量
     */
    fun calculateVelocity(
        inaccuracy: Float,
        baseDirection: Vec3 = Vec3(1.0, 0.0, 0.0)
    ): Vec3 {
        val baseSpeed = speed * (1f + (Math.random().toFloat() - 0.5f) * 0.1f)

        if (inaccuracy <= 0f) {
            return baseDirection.normalize().scale(baseSpeed.toDouble())
        }

        val inaccuracyRad = Math.toRadians(inaccuracy.toDouble())
        val randomYaw = (Math.random() - 0.5) * inaccuracyRad
        val randomPitch = (Math.random() - 0.5) * inaccuracyRad

        // 应用随机旋转
        val yaw = Math.toDegrees(Math.atan2(baseDirection.z, baseDirection.x)) + Math.toDegrees(randomYaw)
        val pitch = Math.toDegrees(Math.asin(baseDirection.y)) + Math.toDegrees(randomPitch)

        val radYaw = Math.toRadians(yaw)
        val radPitch = Math.toRadians(pitch)

        return Vec3(
            Math.cos(radPitch) * Math.cos(radYaw),
            Math.sin(radPitch),
            Math.cos(radPitch) * Math.sin(radYaw)
        ).normalize().scale(baseSpeed.toDouble())
    }

    /**
     * 计算穿透后的伤害
     * @param pierceCount 当前穿透次数
     * @return 实际伤害
     */
    fun calculatePierceDamage(pierceCount: Int): Float {
        return if (pierce) {
            damage * Math.pow(pierceDamageFactor.toDouble(), pierceCount.toDouble()).toFloat()
        } else {
            damage
        }
    }

    /**
     * 检查是否可以命中目标类型
     * @param isFlying 目标是否在空中
     */
    fun canHit(isFlying: Boolean): Boolean {
        return if (isFlying) collidesAir else collidesGround
    }

    companion object {
        /**
         * 无效的子弹类型
         */
        val NONE = BulletType(damage = 0f)

        // ========== 投射物工厂方法 ==========

        /**
         * 创建基础子弹
         * @param damage 基础伤害
         * @param speed 速度
         */
        fun basic(
            damage: Float,
            speed: Float = 3f
        ) = BulletType(
            damage = damage,
            speed = speed,
            lifetime = 60,
            hitEffect = EffectType.autoHitEffect(damage)
        )

        /**
         * 创建穿透子弹
         * @param damage 基础伤害
         * @param pierceCount 穿透数量
         * @param speed 速度
         */
        fun piercing(
            damage: Float,
            pierceCount: Int = 2,
            speed: Float = 3f
        ) = BulletType(
            damage = damage,
            speed = speed,
            pierce = true,
            pierceCap = pierceCount,
            pierceDamageFactor = 0.8f,
            lifetime = 60,
            hitEffect = EffectType.MEDIUM_HIT
        )

        /**
         * 创建范围伤害子弹
         * @param damage 基础伤害
         * @param splashDamage 范围伤害
         * @param splashRadius 范围半径
         * @param speed 速度
         */
        fun splash(
            damage: Float,
            splashDamage: Float,
            splashRadius: Float,
            speed: Float = 2.5f
        ) = BulletType(
            damage = damage,
            speed = speed,
            splashDamage = splashDamage,
            splashRadius = splashRadius,
            lifetime = 50,
            hitEffect = EffectType.autoExplosionEffect(splashDamage, splashRadius),
            despawnEffect = EffectType.SMALL_EXPLOSION
        )

        /**
         * 创建追踪子弹
         * @param damage 基础伤害
         * @param homingPower 追踪强度
         * @param homingRange 追踪范围
         * @param speed 速度
         */
        fun homing(
            damage: Float,
            homingPower: Float = 0.1f,
            homingRange: Float = 10f,
            speed: Float = 2f
        ) = BulletType(
            damage = damage,
            speed = speed,
            homing = true,
            homingPower = homingPower,
            homingRange = homingRange,
            lifetime = 80,
            hitEffect = EffectType.MEDIUM_HIT,
            trailEffect = EffectType.ENERGY_TRAIL
        )

        /**
         * 创建追踪范围伤害子弹（导弹）
         * @param damage 基础伤害
         * @param splashDamage 范围伤害
         * @param splashRadius 范围半径
         * @param speed 速度
         */
        fun missile(
            damage: Float,
            splashDamage: Float,
            splashRadius: Float,
            speed: Float = 2f
        ) = BulletType(
            damage = damage,
            speed = speed,
            splashDamage = splashDamage,
            splashRadius = splashRadius,
            homing = true,
            homingPower = 0.15f,
            homingRange = 15f,
            lifetime = 100,
            hitEffect = EffectType.autoExplosionEffect(splashDamage, splashRadius),
            trailEffect = EffectType.SMOKE_TRAIL
        )

        // ========== 激光工厂方法 ==========

        /**
         * 创建基础激光
         * @param damagePerSecond 每秒伤害
         * @param color 激光颜色
         * @param width 激光宽度
         */
        fun laser(
            damagePerSecond: Float,
            color: Int = 0xFF0000,
            width: Float = 0.5f
        ) = BulletType(
            damage = damagePerSecond,
            speed = 0f,
            instant = true,
            continuous = true,
            laserWidth = width,
            color = color,
            collidesAir = true,
            collidesGround = true,
            shootEffect = EffectType.LASER_SHOOT,
            hitEffect = EffectType.LASER_HIT
        )

        /**
         * 创建穿透激光
         * @param damagePerSecond 每秒伤害
         * @param pierceCount 穿透数量
         * @param color 激光颜色
         */
        fun laserPiercing(
            damagePerSecond: Float,
            pierceCount: Int = -1,
            color: Int = 0x00FF00
        ) = BulletType(
            damage = damagePerSecond,
            speed = 0f,
            instant = true,
            continuous = true,
            pierce = true,
            pierceCap = pierceCount,
            pierceDamageFactor = 1f,
            laserWidth = 1f,
            color = color,
            shootEffect = EffectType.LASER_SHOOT,
            hitEffect = EffectType.LASER_HIT
        )

        /**
         * 创建带效果的激光
         * @param damagePerSecond 每秒伤害
         * @param color 激光颜色
         * @param statusEffects 状态效果列表
         */
        fun laserWithEffects(
            damagePerSecond: Float,
            color: Int = 0xFF5500,
            statusEffects: List<StatusEffectInstance> = emptyList()
        ) = BulletType(
            damage = damagePerSecond,
            speed = 0f,
            instant = true,
            continuous = true,
            laserWidth = 0.7f,
            color = color,
            statusEffects = statusEffects,
            shootEffect = EffectType.LASER_SHOOT,
            hitEffect = EffectType.FIRE_BURST
        )

        // ========== 光束工厂方法 ==========

        /**
         * 创建瞬间光束
         * @param damage 伤害
         * @param pierceCount 穿透数量
         * @param color 光束颜色
         */
        fun beam(
            damage: Float,
            pierceCount: Int = 0,
            color: Int = 0xFFFF00
        ) = BulletType(
            damage = damage,
            speed = 0f,
            instant = true,
            continuous = false,
            pierce = pierceCount > 0,
            pierceCap = pierceCount,
            laserWidth = 0.8f,
            color = color,
            shootEffect = EffectType.BEAM_SHOOT,
            hitEffect = EffectType.ELECTRIC_SPARK
        )

        // ========== 高级自定义 ==========

        /**
         * 完全自定义子弹类型
         */
        fun custom(
            damage: Float,
            speed: Float = 1f,
            lifetime: Int = 60,
            splashDamage: Float = 0f,
            splashRadius: Float = 0f,
            pierce: Boolean = false,
            pierceCap: Int = -1,
            homing: Boolean = false,
            instant: Boolean = false,
            continuous: Boolean = false,
            color: Int = 0xFFFFFF,
            statusEffects: List<StatusEffectInstance> = emptyList()
        ) = BulletType(
            damage = damage,
            speed = speed,
            lifetime = lifetime,
            splashDamage = splashDamage,
            splashRadius = splashRadius,
            pierce = pierce,
            pierceCap = pierceCap,
            homing = homing,
            instant = instant,
            continuous = continuous,
            color = color,
            statusEffects = statusEffects
        )
    }
}