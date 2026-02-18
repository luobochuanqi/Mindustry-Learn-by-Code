package xyz.luobo.mindustry.core.turret

import net.minecraft.world.item.Items
import net.minecraft.world.level.material.Fluids
import xyz.luobo.mindustry.core.turret.ammo.AmmoStats
import xyz.luobo.mindustry.core.turret.ammo.LaserStats
import xyz.luobo.mindustry.core.turret.liquid.TurretLiquid

/**
 * 炮台配置示例
 * 展示如何创建各种类型的炮台配置
 */
object TurretExamples {

    /**
     * 示例 1：基础弹药炮台
     * 使用铜锭作为弹药，简单的攻击逻辑
     */
    val basicAmmoTurret = TurretConfig.builder(
        identifier = "basic_turret",
        description = "基础弹药炮台"
    )
        .range(10f)
        .fireRate(1f)
        .inaccuracy(2)
        .ammoCapacity(200)
        .addAmmo(AmmoStats.basic(Items.COPPER_INGOT, 5f))
        .build()

    /**
     * 示例 2：激光炮台
     * 持续发射激光，消耗电力
     */
    val laserTurret = TurretConfig.builder(
        identifier = "laser_turret",
        description = "激光炮台"
    )
        .range(20f)
        .energyCapacity(15000)
        .energyConsumptionPerSecond(120f)
        .laserStats(LaserStats.basic(15f, 0xFF0000.toInt()))
        .canAttackAir(true)
        .canAttackGround(true)
        .inaccuracy(0)
        .build()

    /**
     * 示例 3：穿透激光炮台
     * 激光可以穿透多个敌人
     */
    val piercingLaserTurret = TurretConfig.builder(
        identifier = "piercing_laser",
        description = "穿透激光炮台"
    )
        .range(25f)
        .energyCapacity(20000)
        .energyConsumptionPerSecond(180f)
        .laserStats(LaserStats.piercing(20f, 5, 0x00FF00.toInt()))
        .canAttackAir(true)
        .canAttackGround(true)
        .inaccuracy(0)
        .build()

    /**
     * 示例 4：范围伤害炮台
     * 发射爆炸弹，造成范围伤害
     */
    val splashTurret = TurretConfig.builder(
        identifier = "splash_turret",
        description = "范围伤害炮台"
    )
        .range(15f)
        .fireRate(0.5f)
        .inaccuracy(10)
        .ammoCapacity(100)
        .addAmmo(AmmoStats.splash(Items.TNT, 20f, 15f, 4f))
        .canAttackAir(true)
        .canAttackGround(true)
        .build()

    /**
     * 示例 5：追踪炮台
     * 发射追踪导弹
     */
    val homingTurret = TurretConfig.builder(
        identifier = "homing_turret",
        description = "追踪炮台"
    )
        .range(20f)
        .fireRate(0.3f)
        .inaccuracy(5)
        .ammoCapacity(50)
        .addAmmo(AmmoStats.homing(Items.FIRE_CHARGE, 25f, 180f, 50f))
        .canAttackAir(true)
        .canAttackGround(false)
        .build()

    /**
     * 示例 6：带液体强化的炮台
     * 使用水增强开火速率
     */
    val boostedTurret = TurretConfig.builder(
        identifier = "boosted_turret",
        description = "带液体强化的炮台"
    )
        .range(12f)
        .fireRate(1.5f)
        .inaccuracy(3)
        .ammoCapacity(150)
        .addAmmo(AmmoStats.basic(Items.IRON_INGOT, 8f))
        .liquidCapacity(2000)
        .addLiquid(TurretLiquid.forBoost(Fluids.WATER, 10, 0.2f))
        .boostLiquid(Fluids.WATER, 100)
        .canAttackAir(true)
        .canAttackGround(true)
        .build()

    /**
     * 示例 7：防空专用炮台
     * 只攻击空中单位，高射速
     */
    val antiAirTurret = TurretConfig.builder(
        identifier = "anti_air",
        description = "防空炮台"
    )
        .range(18f)
        .fireRate(3f)
        .inaccuracy(8)
        .ammoCapacity(300)
        .addAmmo(AmmoStats.basic(Items.ARROW, 3f))
        .canAttackAir(true)
        .canAttackGround(false)
        .build()

    /**
     * 示例 8：对地专用炮台
     * 只攻击地面单位，高伤害
     */
    val antiGroundTurret = TurretConfig.builder(
        identifier = "anti_ground",
        description = "对地炮台"
    )
        .range(14f)
        .fireRate(0.8f)
        .inaccuracy(1)
        .ammoCapacity(120)
        .addAmmo(AmmoStats.basic(Items.DIAMOND, 15f))
        .canAttackAir(false)
        .canAttackGround(true)
        .build()

    /**
     * 示例 9：多功能弹药炮台
     * 支持多种弹药，每种有不同的效果
     */
    val multiAmmoTurret = TurretConfig.builder(
        identifier = "multi_ammo",
        description = "多功能弹药炮台"
    )
        .range(16f)
        .fireRate(1.2f)
        .inaccuracy(4)
        .ammoCapacity(250)
        // 基础弹药
        .addAmmo(AmmoStats.basic(Items.COPPER_INGOT, 5f))
        // 范围伤害弹药
        .addAmmo(AmmoStats.splash(Items.TNT, 15f, 10f, 3f))
        // 追踪弹药
        .addAmmo(AmmoStats.homing(Items.FIRE_CHARGE, 20f, 120f, 40f))
        .canAttackAir(true)
        .canAttackGround(true)
        .build()

    /**
     * 示例 10：高级激光炮台
     * 大容量电力，高伤害，绿色激光
     */
    val advancedLaserTurret = TurretConfig.builder(
        identifier = "advanced_laser",
        description = "高级激光炮台"
    )
        .range(30f)
        .energyCapacity(30000)
        .energyConsumptionPerSecond(200f)
        .laserStats(LaserStats.basic(25f, 0x00FF00.toInt()))
        .canAttackAir(true)
        .canAttackGround(true)
        .inaccuracy(0)
        .build()
}

/**
 * 炮台配置使用指南
 *
 * 1. 弹药炮台：
 *    - 设置 ammoCapacity 和 ammoStats
 *    - 实现 fireProjectile() 方法
 *    - 子类中重写 consumeAmmo() 消耗弹药
 *
 * 2. 激光炮台：
 *    - 设置 energyCapacity 和 energyConsumptionPerSecond
 *    - 设置 laserStats
 *    - 重写 fireLaser() 方法进行激光渲染
 *    - 无需实现 fireProjectile()
 *
 * 3. 液体强化：
 *    - 设置 liquidCapacity 和 supportedLiquids
 *    - 设置 boostLiquid 和 boostLiquidAmount
 *    - 调用 activateBoost() 激活强化
 *
 * 4. 弹药类型（可组合使用）：
 *    - 基础弹药：AmmoStats.basic() - 只有基础伤害
 *    - 范围伤害弹药：AmmoStats.splash() - 爆炸造成范围伤害
 *    - 追踪弹药：AmmoStats.homing() - 追踪目标
 *    - 追踪范围伤害弹药：AmmoStats.homingSplash() - 同时具有追踪和范围伤害
 *    - 爆炸追踪导弹：AmmoStats.explosiveMissile() - 快捷创建追踪范围伤害弹药
 *    - 高级弹药：AmmoStats.advanced() - 完全自定义，可同时包含追踪、范围伤害、效果
 *    - 带效果弹药：AmmoStats.withEffects() - 添加燃烧、冰冻等效果
 *
 * 5. 弹药属性组合：
 *    - 追踪 + 范围伤害：使用 homingSplash() 或 explosiveMissile()
 *    - 追踪 + 效果：使用 advanced() 设置 homingStats 和 effects
 *    - 范围伤害 + 效果：使用 advanced() 设置 splashStats 和 effects
 *    - 追踪 + 范围伤害 + 效果：使用 advanced() 同时设置所有属性
 *
 * 6. 检测弹药属性：
 *    - ammoStats.hasSplash - 是否具有范围伤害
 *    - ammoStats.hasTracking - 是否具有追踪能力
 *    - ammoStats.hasEffects - 是否有任何效果
 *    - ammoStats.splashStats - 获取范围伤害统计
 *    - ammoStats.homingStats - 获取追踪统计
 *
 * 7. 参数默认值：
 *    - 未指定的参数默认为 0
 *    - 激光默认为无（LaserStats.NONE）
 *    - 弹药默认为空列表
 *    - 液体默认为空列表
 *    - 电力默认为 0
 *    - 范围伤害默认为无（SplashStats.NONE）
 *    - 追踪默认为无（HomingStats.NONE）
 *
 * 8. 检测炮台类型：
 *    - config.isLaserTurret - 是否是激光炮台
 *    - config.isAmmoTurret - 是否是弹药炮台
 *    - config.hasEnergySupport - 是否支持电力
 *    - config.hasAmmoSystem - 是否有弹药系统
 *    - config.hasLiquidSupport - 是否支持液体
 *    - config.hasBoostSupport - 是否支持强化
 */