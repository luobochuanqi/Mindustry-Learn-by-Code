package xyz.luobo.mturrets.common.turrets

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.core.turret.bullet.BulletType
import xyz.luobo.mturrets.core.turret.bullet.EffectType
import xyz.luobo.mturrets.core.turret.config.TurretConfig
import xyz.luobo.mturrets.core.turret.entity.PowerTurretBlockEntity

/**
  * LEGACY: Arc 本期保留运行、不迁不删(ADR-0008 边界);换代按 ADR-0006 瞬时家族 + ADR-0007 耗电接入点重建。
 * Arc 炮台
 * 电力炮台，发射电弧攻击
 * 瞬间命中，可以施加电击效果
 */
class ArcTurretBlockEntity(
    pos: BlockPos,
    state: BlockState
) : PowerTurretBlockEntity(
    ModBlockEntityTypes.ARC_BLOCK_ENTITY.get(),
    pos,
    state
) {
    override val config = TurretConfig(
        identifier = "arc",
        description = "Fires arcs of electricity at enemies. Requires power.",
        range = 15f,
        reloadTime = 5f,  // 很快
        inaccuracy = 0f,
        targetAir = true,
        targetGround = true,
        rotateSpeed = 8f,
        recoil = 0f,
        shake = 0.5f
    )

    override val shootType = BulletType(
        damage = 12f,
        speed = 0f,  // 瞬间命中
        instant = true,
        lifetime = 1,
        color = 0x00FFFF,  // 青色电弧
        shootEffect = EffectType.ELECTRIC_SPARK,
        hitEffect = EffectType.ELECTRIC_SPARK
    )

    override val powerPerShot = 80
    override val powerCapacity = 5000
    override val maxPowerInput = 500

    override fun fireProjectile(level: Level, pos: BlockPos, target: LivingEntity) {
        // 电弧是瞬间命中，不需要创建投射物实体
        // 直接造成伤害

        // 造成伤害
        target.hurt(level.damageSources().magic(), shootType.damage)

        // 应用状态效果
        // 这里简化处理，实际应该应用电击效果

        // 创建电弧视觉效果
        createArcEffect(level, getShootPos(), target.position())

        onShoot(level, pos)
    }

    /**
     * 创建电弧视觉效果
     */
    private fun createArcEffect(level: Level, from: net.minecraft.world.phys.Vec3, to: net.minecraft.world.phys.Vec3) {
        if (level.isClientSide) {
            // 客户端：创建粒子效果模拟电弧
            val distance = from.distanceTo(to)
            val steps = (distance * 2).toInt()

            for (i in 0 until steps) {
                val t = i.toDouble() / steps
                val basePos = from.lerp(to, t)

                // 添加随机偏移模拟电弧抖动
                val jitter = 0.1
                val jitterX = (Math.random() - 0.5) * jitter
                val jitterY = (Math.random() - 0.5) * jitter
                val jitterZ = (Math.random() - 0.5) * jitter

                level.addParticle(
                    net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    basePos.x + jitterX,
                    basePos.y + jitterY,
                    basePos.z + jitterZ,
                    0.0, 0.0, 0.0
                )
            }
        }
    }

    override fun onShoot(level: Level, pos: BlockPos) {
        super.onShoot(level, pos)

        // 播放电弧音效
        // level.playSound(null, pos, ModSounds.ARC_SHOOT.get(), SoundSource.BLOCKS, 0.8f, 1.0f)
    }
}