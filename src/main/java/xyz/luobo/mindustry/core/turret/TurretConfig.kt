package xyz.luobo.mindustry.core.turret

import net.minecraft.world.item.Item
import net.minecraft.world.level.material.Fluid
import xyz.luobo.mindustry.core.turret.ammo.AmmoStats
import xyz.luobo.mindustry.core.turret.ammo.LaserStats
import xyz.luobo.mindustry.core.turret.liquid.TurretLiquid

/**
 * 炮台配置
 * 定义炮台的所有属性和行为
 * 支持弹药型炮台、激光型炮台或混合型炮台
 */
data class TurretConfig(
    // ========== 基本信息 ==========

    /** 英文标识名 */
    val identifier: String,

    /** 用途描述 */
    val description: String,

    // ========== 电力系统 ==========

    /** 电力容量（FE） */
    val energyCapacity: Int = 0,

    /** 电力消耗（FE/s，每秒消耗） */
    val energyConsumptionPerSecond: Float = 0f,

    // ========== 液体系统 ==========

    /** 液体容量 */
    val liquidCapacity: Int = 0,

    /** 支持的液体列表 */
    val supportedLiquids: List<TurretLiquid> = emptyList(),

    // ========== 功能属性 ==========

    /** 射击范围（格） */
    val range: Float = 10f,

    /** 射击误差（度，0 表示完全精准） */
    val inaccuracy: Int = 0,

    /** 基础开火速率（每秒射击次数） */
    val fireRate: Float = 1f,

    /** 是否攻击空中单位 */
    val canAttackAir: Boolean = true,

    /** 是否攻击地面单位 */
    val canAttackGround: Boolean = true,

    // ========== 弹药系统 ==========

    /** 弹药容量 */
    val ammoCapacity: Int = 0,

    /** 弹药统计列表（支持的弹药及其属性） */
    val ammoStats: List<AmmoStats> = emptyList(),

    // ========== 激光系统 ==========

    /** 激光统计 */
    val laserStats: LaserStats = LaserStats.NONE,

    // ========== 强化系统 ==========

    /** 强化所需液体（可选，如果为 null 则不支持强化） */
    val boostLiquid: Fluid? = null,

    /** 强化所需液体消耗量 */
    val boostLiquidAmount: Int = 0
) {
    // ========== 计算属性 ==========

    /** 是否支持电力 */
    val hasEnergySupport: Boolean
        get() = energyCapacity > 0 && energyConsumptionPerSecond > 0

    /** 是否支持液体 */
    val hasLiquidSupport: Boolean
        get() = liquidCapacity > 0 && supportedLiquids.isNotEmpty()

    /** 是否支持强化 */
    val hasBoostSupport: Boolean
        get() = boostLiquid != null && boostLiquidAmount > 0

    /** 是否有弹药系统 */
    val hasAmmoSystem: Boolean
        get() = ammoCapacity > 0 && ammoStats.isNotEmpty()

    /** 是否是激光炮台 */
    val isLaserTurret: Boolean
        get() = laserStats.isEnabled

    /** 是否是弹药炮台 */
    val isAmmoTurret: Boolean
        get() = hasAmmoSystem

    /** 每次射击的冷却时间（ticks） */
    val cooldownTicks: Int
        get() = if (fireRate > 0) (20f / fireRate).toInt() else Int.MAX_VALUE

    // ========== 查询方法 ==========

    /** 每次射击的电力消耗（FE） */
    val energyConsumptionPerShot: Float
        get() = if (fireRate > 0) energyConsumptionPerSecond / fireRate else 0f

    /**
     * 获取物品对应的弹药统计
     */
    fun getAmmoStats(item: Item): AmmoStats? {
        return ammoStats.find { it.item == item }
    }

    /**
     * 获取液体对应的配置
     */
    fun getLiquidConfig(fluid: Fluid): TurretLiquid? {
        return supportedLiquids.find { it.fluid == fluid }
    }

    /**
     * 检查是否支持该液体
     */
    fun supportsLiquid(fluid: Fluid): Boolean {
        return supportedLiquids.any { it.fluid == fluid }
    }

    /**
     * 检查是否支持该弹药
     */
    fun supportsAmmo(item: Item): Boolean {
        return ammoStats.any { it.item == item }
    }

    // ========== 构建器 ==========

    companion object {
        /**
         * 创建构建器
         */
        fun builder(identifier: String, description: String) = Builder(identifier, description)
    }

    /**
     * 炮台配置构建器
     */
    class Builder(
        private val identifier: String,
        private val description: String
    ) {
        private var energyCapacity: Int = 0
        private var energyConsumptionPerSecond: Float = 0f
        private var liquidCapacity: Int = 0
        private var supportedLiquids: List<TurretLiquid> = emptyList()
        private var range: Float = 10f
        private var inaccuracy: Int = 0
        private var fireRate: Float = 1f
        private var canAttackAir: Boolean = true
        private var canAttackGround: Boolean = true
        private var ammoCapacity: Int = 0
        private var ammoStats: List<AmmoStats> = emptyList()
        private var laserStats: LaserStats = LaserStats.NONE
        private var boostLiquid: Fluid? = null
        private var boostLiquidAmount: Int = 0

        fun energyCapacity(capacity: Int) = apply { this.energyCapacity = capacity }

        fun energyConsumptionPerSecond(consumption: Float) = apply { this.energyConsumptionPerSecond = consumption }

        fun liquidCapacity(capacity: Int) = apply { this.liquidCapacity = capacity }

        fun supportedLiquids(liquids: List<TurretLiquid>) = apply { this.supportedLiquids = liquids }

        fun addLiquid(liquid: TurretLiquid) = apply {
            this.supportedLiquids = supportedLiquids + liquid
        }

        fun range(range: Float) = apply { this.range = range }

        fun inaccuracy(inaccuracy: Int) = apply { this.inaccuracy = inaccuracy }

        fun fireRate(fireRate: Float) = apply { this.fireRate = fireRate }

        fun canAttackAir(can: Boolean) = apply { this.canAttackAir = can }

        fun canAttackGround(can: Boolean) = apply { this.canAttackGround = can }

        fun ammoCapacity(capacity: Int) = apply { this.ammoCapacity = capacity }

        fun ammoStats(stats: List<AmmoStats>) = apply { this.ammoStats = stats }

        fun addAmmo(stat: AmmoStats) = apply {
            this.ammoStats += stat
        }

        fun laserStats(stats: LaserStats) = apply { this.laserStats = stats }

        fun boostLiquid(fluid: Fluid, amount: Int) = apply {
            this.boostLiquid = fluid
            this.boostLiquidAmount = amount
        }

        fun build() = TurretConfig(
            identifier = identifier,
            description = description,
            energyCapacity = energyCapacity,
            energyConsumptionPerSecond = energyConsumptionPerSecond,
            liquidCapacity = liquidCapacity,
            supportedLiquids = supportedLiquids,
            range = range,
            inaccuracy = inaccuracy,
            fireRate = fireRate,
            canAttackAir = canAttackAir,
            canAttackGround = canAttackGround,
            ammoCapacity = ammoCapacity,
            ammoStats = ammoStats,
            laserStats = laserStats,
            boostLiquid = boostLiquid,
            boostLiquidAmount = boostLiquidAmount
        )
    }
}

// ========== 预设配置 ==========

/**
 * 创建基础弹药炮台配置
 */
fun basicTurretConfig(
    identifier: String,
    description: String,
    range: Float = 10f,
    fireRate: Float = 1f
) = TurretConfig.builder(identifier, description)
    .range(range)
    .fireRate(fireRate)
    .canAttackAir(true)
    .canAttackGround(true)
    .build()

/**
 * 创建防空炮台配置
 */
fun antiAirTurretConfig(
    identifier: String,
    description: String,
    range: Float = 15f,
    fireRate: Float = 2f
) = TurretConfig.builder(identifier, description)
    .range(range)
    .fireRate(fireRate)
    .canAttackAir(true)
    .canAttackGround(false)
    .inaccuracy(5)
    .build()

/**
 * 创建对地炮台配置
 */
fun antiGroundTurretConfig(
    identifier: String,
    description: String,
    range: Float = 12f,
    fireRate: Float = 1.5f
) = TurretConfig.builder(identifier, description)
    .range(range)
    .fireRate(fireRate)
    .canAttackAir(false)
    .canAttackGround(true)
    .inaccuracy(2)
    .build()

/**
 * 创建激光炮台配置
 */
fun laserTurretConfig(
    identifier: String,
    description: String,
    damagePerSecond: Float,
    range: Float = 15f,
    energyConsumption: Float = 100f,
    color: Int = 0xFF0000.toInt()
) = TurretConfig.builder(identifier, description)
    .range(range)
    .energyCapacity(10000)
    .energyConsumptionPerSecond(energyConsumption)
    .laserStats(LaserStats.basic(damagePerSecond, color))
    .canAttackAir(true)
    .canAttackGround(true)
    .inaccuracy(0) // 激光无误差
    .build()

/**
 * 创建穿透激光炮台配置
 */
fun piercingLaserTurretConfig(
    identifier: String,
    description: String,
    damagePerSecond: Float,
    pierceCount: Int,
    range: Float = 20f,
    energyConsumption: Float = 150f,
    color: Int = 0x00FF00
) = TurretConfig.builder(identifier, description)
    .range(range)
    .energyCapacity(15000)
    .energyConsumptionPerSecond(energyConsumption)
    .laserStats(LaserStats.piercing(damagePerSecond, pierceCount, color))
    .canAttackAir(true)
    .canAttackGround(true)
    .inaccuracy(0)
    .build()