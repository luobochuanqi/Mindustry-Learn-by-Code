package xyz.luobo.mindustry.common.turrets.duo

import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.LargeFireball
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import software.bernie.geckolib.animatable.GeoBlockEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil
import xyz.luobo.mindustry.common.ModBlockEntityTypes
import xyz.luobo.mindustry.core.turret.BaseTurretBE
import xyz.luobo.mindustry.core.turret.TurretConfig

/**
 * Duo 炮台方块实体
 * 基于 GeckoLib 的 3D 炮台，发射恶魂火焰弹
 */
class DuoBE(
    pos: BlockPos,
    blockState: BlockState
) : BaseTurretBE(ModBlockEntityTypes.DUO_Block_Entity.get(), pos, blockState), GeoBlockEntity {

    // ========== GeckoLib 动画 ==========

    private val animatableInstanceCache: AnimatableInstanceCache =
        GeckoLibUtil.createInstanceCache(this)

    val DEPLOY_ANIM: RawAnimation = RawAnimation.begin().thenPlay("animation")

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(AnimationController(this) { state ->
            state.setAndContinue(DEPLOY_ANIM)
        })
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache {
        return animatableInstanceCache
    }

    // ========== 炮台配置 ==========

    override val config: TurretConfig = TurretConfig.builder(
        identifier = "duo",
        description = "Fires alternating bullets at enemies."
    )
        .range(20f)
        .fireRate(3f)  // 每秒 2 次
        .inaccuracy(2) // 轻微误差
        .canAttackAir(true)
        .canAttackGround(true)
        .ammoCapacity(30)
        .build()

    override val rotationSpeed: Float = 180.0f // 每秒最大旋转角度（度）

    /**
     * 发射恶魂火焰弹
     */
    override fun fireProjectile(level: Level, pos: BlockPos, target: LivingEntity) {
        // 计算射击方向（考虑误差）
        val direction = calculateFireDirection(target)

        // 炮口位置（方块中心向上1.5格）
        val fireballPos = pos.center.add(0.0, 1.5, 0.0)

        // 计算速度（0.5倍速）
        val velocity = direction.scale(0.5)

        // 创建恶魂火焰弹
        val fireball = LargeFireball(level, target, velocity, 0)
        fireball.setPos(fireballPos)

        // 添加到世界
        level.addFreshEntity(fireball)

        // 播放发射音效
        level.playSound(
            null,
            pos,
            SoundEvents.GHAST_SHOOT,
            SoundSource.BLOCKS,
            2.0f, // 音量
            1.0f  // 音调
        )

        // 添加粒子效果
        if (level is ServerLevel) {
            level.sendParticles(
                ParticleTypes.FLAME,
                fireballPos.x, fireballPos.y, fireballPos.z,
                15,    // 数量
                0.1, 0.1, 0.1,  // X/Y/Z偏移
                0.05   // 速度
            )

            level.sendParticles(
                ParticleTypes.LARGE_SMOKE,
                fireballPos.x, fireballPos.y, fireballPos.z,
                10,
                0.1, 0.1, 0.1,
                0.01
            )
        }
    }
}