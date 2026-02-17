package xyz.luobo.mindustry.common.turrets.laser

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mindustry.core.turret.BaseTurretBE
import xyz.luobo.mindustry.core.turret.TurretConfig
import xyz.luobo.mindustry.core.turret.laser.LaserStats

/**
 * 激光炮台示例
 * 持续发射激光，消耗电力
 */
class LaserTurretBE(
    pos: BlockPos,
    blockState: BlockState
) : BaseTurretBE(blockEntityType, pos, blockState) {

    companion object {
        // TODO: 在 ModBlockEntityTypes 中注册
        lateinit var blockEntityType: net.minecraft.world.level.block.entity.BlockEntityType<LaserTurretBE>
    }

    override val config: TurretConfig = TurretConfig.builder(
        identifier = "laser_turret",
        description = "激光炮台，持续发射激光"
    )
        .range(20f)
        .energyCapacity(15000)
        .energyConsumptionPerSecond(120f)
        .laserStats(LaserStats.basic(15f, 0xFF0000.toInt())) // 红色激光，15伤害/秒
        .canAttackAir(true)
        .canAttackGround(true)
        .inaccuracy(0) // 激光无误差
        .build()

    override val rotationSpeed: Float = 360f // 快速旋转

    /**
     * 激光炮台不需要发射投射物
     */
    override fun fireProjectile(level: Level, pos: BlockPos, target: LivingEntity) {
        // 无操作
    }

    /**
     * 自定义激光渲染逻辑
     */
    override fun fireLaser(level: Level, pos: BlockPos, target: LivingEntity, laserStats: LaserStats) {
        // TODO: 实现激光渲染
        // 可以在这里添加激光粒子效果、声音等
    }
}