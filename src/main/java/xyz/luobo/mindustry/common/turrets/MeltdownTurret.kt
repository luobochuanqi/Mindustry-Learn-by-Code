package xyz.luobo.mindustry.common.turrets

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mindustry.common.ModBlockEntityTypes
import xyz.luobo.mindustry.core.turret.bullet.BulletType
import xyz.luobo.mindustry.core.turret.bullet.EffectType
import xyz.luobo.mindustry.core.turret.bullet.StatusEffect
import xyz.luobo.mindustry.core.turret.bullet.StatusEffectInstance
import xyz.luobo.mindustry.core.turret.config.TurretConfig
import xyz.luobo.mindustry.core.turret.entity.ContinuousTurretBlockEntity

/**
 * Meltdown 炮台
 * 重型激光炮台
 * 持续发射高伤害激光，可以穿透多个敌人
 * 需要大量电力
 */
class MeltdownTurretBlockEntity(
    pos: BlockPos,
    state: BlockState
) : ContinuousTurretBlockEntity(
    ModBlockEntityTypes.MELTDOWN_BLOCK_ENTITY.get(),
    pos,
    state
) {
    override val config = TurretConfig(
        identifier = "meltdown",
        description = "Fires a massive continuous laser beam at enemies. Requires large amounts of power.",
        range = 25f,
        reloadTime = 60f,  // 预热时间
        inaccuracy = 0f,
        targetAir = true,
        targetGround = true,
        rotateSpeed = 2f,  // 慢速旋转
        recoil = 0f,
        shake = 1f,
        shootWarmupSpeed = 0.02f,  // 慢速预热
        minWarmup = 0.8f  // 需要较高预热才能射击
    )

    override val shootType = BulletType(
        damage = 60f,  // 每秒60伤害
        speed = 0f,
        instant = true,
        continuous = true,
        color = 0xFF0000,  // 红色激光
        laserWidth = 1.5f,
        pierce = true,
        pierceCap = -1,  // 无限穿透
        pierceDamageFactor = 1f,  // 穿透不衰减
        statusEffects = listOf(
            StatusEffectInstance(StatusEffect.BURNING, 100, 1)
        ),
        shootEffect = EffectType.LASER_SHOOT,
        hitEffect = EffectType.FIRE_BURST,
        trailEffect = EffectType.LARGE_TRAIL
    )

    override val powerPerSecond = 300f  // 每秒300电力
    override val powerPerShot = 100     // 初始射击消耗
    override val powerCapacity = 20000
    override val maxPowerInput = 2000

    override val damageInterval = 4  // 每0.2秒造成一次伤害
    override val requiresWarmupToFire = true
    override val minWarmupToFire = 0.8f

    override fun onContinuousFireTick(level: Level, pos: BlockPos, target: LivingEntity) {
        // 创建激光渲染效果
        createLaserEffect(level, getShootPos(), target.position())

        // 对激光路径上的所有实体造成伤害
        damageEntitiesInLaserPath(level, pos, target)
    }

    /**
     * 对激光路径上的所有实体造成伤害
     */
    private fun damageEntitiesInLaserPath(
        level: Level,
        pos: BlockPos,
        primaryTarget: LivingEntity
    ) {
        val start = getShootPos()
        val end = primaryTarget.position()
        val direction = end.subtract(start).normalize()

        // 在激光路径上查找所有实体
        val laserRange = config.range.toDouble()
        val searchArea = net.minecraft.world.phys.AABB(start, end).inflate(1.0)

        val entities = level.getEntitiesOfClass(
            LivingEntity::class.java,
            searchArea
        ) { it != primaryTarget && it.isAlive && isValidTarget(it) }

        // 对路径上的实体造成伤害（穿透效果）
        var damageMultiplier = 1f
        entities.forEach { entity ->
            // 检查实体是否在激光路径上
            if (isEntityInLaserPath(entity, start, direction, laserRange)) {
                val damage = (shootType.damage * damageInterval / 20f) * damageMultiplier
                entity.hurt(level.damageSources().magic(), damage)

                // 穿透不衰减，但记录伤害统计
                totalDamageDealt += damage
            }
        }
    }

    /**
     * 检查实体是否在激光路径上
     */
    private fun isEntityInLaserPath(
        entity: LivingEntity,
        laserStart: net.minecraft.world.phys.Vec3,
        laserDir: net.minecraft.world.phys.Vec3,
        maxRange: Double
    ): Boolean {
        val entityPos = entity.position()
        val toEntity = entityPos.subtract(laserStart)

        // 投影到激光方向
        val projectionLength = toEntity.dot(laserDir)
        if (projectionLength < 0 || projectionLength > maxRange) return false

        // 计算垂直距离
        val projection = laserDir.scale(projectionLength)
        val perpendicular = toEntity.subtract(projection)
        val distance = perpendicular.length()

        // 如果距离小于激光宽度+实体半径，认为在路径上
        return distance < (shootType.laserWidth + entity.bbWidth / 2)
    }

    /**
     * 创建激光视觉效果
     */
    private fun createLaserEffect(
        level: Level,
        from: net.minecraft.world.phys.Vec3,
        to: net.minecraft.world.phys.Vec3
    ) {
        if (level.isClientSide) {
            val distance = from.distanceTo(to)
            val steps = (distance * 4).toInt()  // 高密度粒子

            for (i in 0..steps) {
                val t = i.toDouble() / steps
                val pos = from.lerp(to, t)

                // 主激光粒子
                level.addParticle(
                    net.minecraft.core.particles.ParticleTypes.FLAME,
                    pos.x,
                    pos.y,
                    pos.z,
                    0.0, 0.0, 0.0
                )

                // 偶尔添加火花
                if (i % 5 == 0) {
                    level.addParticle(
                        net.minecraft.core.particles.ParticleTypes.LAVA,
                        pos.x + (Math.random() - 0.5) * 0.5,
                        pos.y + (Math.random() - 0.5) * 0.5,
                        pos.z + (Math.random() - 0.5) * 0.5,
                        0.0, 0.1, 0.0
                    )
                }
            }
        }
    }

    override fun onHitTarget(level: Level, pos: BlockPos, target: LivingEntity, damage: Float) {
        super.onHitTarget(level, pos, target, damage)

        // 设置目标燃烧
        target.setSecondsOnFire(4)
    }

    override fun onStopFiring() {
        super.onStopFiring()

        // 重置预热（可选：让预热缓慢下降而不是立即归零）
        // warmup = 0f
    }

    override fun onShoot(level: Level, pos: BlockPos) {
        // 持续射击炮台不使用传统的 onShoot
    }
}