package xyz.luobo.mindustry.common.turrets.duo

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.LargeFireball
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import software.bernie.geckolib.animatable.GeoBlockEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil
import xyz.luobo.mindustry.common.ModBlockEntityTypes
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sign
import kotlin.math.sqrt


class DuoBE(
    pos: BlockPos,
    blockState: BlockState
) : BlockEntity(ModBlockEntityTypes.DUO_Block_Entity.get(), pos, blockState), GeoBlockEntity {
    private val animatableInstanceCache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)
    val DEPLOY_ANIM: RawAnimation = RawAnimation.begin().thenPlay("animation")

    var targetYaw: Float = 0f      // 目标方向
    var currentYaw: Float = 0f     // 当前实际方向

    // attack 暂时
    var attackCooldown: Int = 0
    private val ATTACK_COOLDOWN_TICKS = 5

    companion object {
        const val ROTATION_SPEED = 180.0f // 每秒最大旋转角度（度）
    }

    // 返回动画控制器/
    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(AnimationController(this) { state ->
            state.setAndContinue(DEPLOY_ANIM)
        })
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache {
        return animatableInstanceCache
    }

    fun tickServer(level: Level, pos: BlockPos, state: BlockState, be: DuoBE) {
        // 计算旋转角度
        // 注意：此方法只在服务端被调用（DuoBlock.getTicker已检查level.isClientSide）

        // 1. 获取最近的敌人（距离10格内的怪物）
        val target = findNearestTarget(level, pos, 10f)

        if (target != null) {
            // 2. 计算目标Yaw角度
            targetYaw = calculateYawToTarget(pos, target.onPos)
        }

        // 3. 平滑旋转（线性插值）
        var deltaYaw = targetYaw - currentYaw

        // 处理角度环绕问题（例如：350°到10°应该走+20°而不是-340°）
        deltaYaw = Mth.wrapDegrees(deltaYaw)

        // 限制最大旋转速度（线性旋转）
        val maxRotationThisTick = ROTATION_SPEED / 20f // 每tick最大角度
        currentYaw += when {
            abs(deltaYaw) > maxRotationThisTick -> sign(deltaYaw) * maxRotationThisTick
            else -> deltaYaw
        }

        // 4. 标记需要同步到客户端
        if (abs(deltaYaw) > 0.1f) {
            setChanged()
            syncData()
        }

        // 5.攻击 target
        if (target != null && abs(deltaYaw) <= 30f) {
            if (attackCooldown <= 0) {
                // 发射火焰弹
                fireGhastFireball(level, pos, target)
                attackCooldown = ATTACK_COOLDOWN_TICKS
            } else {
                attackCooldown--
            }
        } else {
            // 没有目标或角度不对时重置冷却
            attackCooldown = 0
        }
    }

    /**
     * 发射恶魂火焰弹
     * @param level 世界
     * @param pos 炮台位置
     * @param target 目标实体
     */
    private fun fireGhastFireball(level: Level, pos: BlockPos, target: LivingEntity) {
        // 炮口位置（方块中心向上1.5格）
        val fireballPos = pos.center.add(0.0, 1.5, 0.0)

        // 计算到目标的方向向量
        val dx = target.x - fireballPos.x
        val dy = (target.eyeY - 1.0) - fireballPos.y // 瞄准眼睛位置
        val dz = target.z - fireballPos.z

        // 归一化并设置速度（0.5倍速，可调）
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        if (distance == 0.0) return // 防止除零

        val velocityX = (dx / distance) * 0.5
        val velocityY = (dy / distance) * 0.5
        val velocityZ = (dz / distance) * 0.5

        // 创建恶魂火焰弹（发射者设为null，BlockEntity不是LivingEntity）
        // 暂时设置为几几打叽叽
        val fireball = LargeFireball(level, target, Vec3(velocityX, velocityY, velocityZ), 0)
        fireball.setPos(fireballPos)

        // 添加到世界
        level.addFreshEntity(fireball)

        // 播放发射音效
        level.playSound(
            null, // 玩家
            pos,
            SoundEvents.GHAST_SHOOT,
            SoundSource.BLOCKS,
            2.0f, // 音量
            1.0f  // 音调
        )

        // 在ServerLevel添加粒子效果
        if (level is ServerLevel) {
            level.sendParticles(
                ParticleTypes.FLAME,  // 火焰粒子
                fireballPos.x,
                fireballPos.y,
                fireballPos.z,
                15,              // 数量
                0.1, 0.1, 0.1,   // X/Y/Z偏移
                0.05             // 速度
            )

            // 添加烟雾效果
            level.sendParticles(
                ParticleTypes.LARGE_SMOKE,
                fireballPos.x,
                fireballPos.y,
                fireballPos.z,
                10,
                0.1, 0.1, 0.1,
                0.01
            )
        }
    }

    // 计算到目标的偏航角
    private fun calculateYawToTarget(from: BlockPos, to: BlockPos): Float {
        val dx = to.x - from.x
        val dz = to.z - from.z
        return (atan2(dz.toDouble(), dx.toDouble()) * (180f / Math.PI)).toFloat() - 90f
        // -90f 调整使0度指向+Z
    }

    private fun findNearestTarget(level: Level, pos: BlockPos, range: Float): LivingEntity? {
        val area = AABB(pos).inflate(range.toDouble())
        val enemies = level.getEntitiesOfClass(
            LivingEntity::class.java,
            area
        ) { e -> e.isAlive && (e is Monster || e is Player) }

        return enemies.minByOrNull { it.distanceToSqr(pos.center) }
    }

    // Create an update tag here, like above.
    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        return saveWithoutMetadata(registries)
    }

    // Return our packet here. This method returning a non-null result tells the game to use this packet for syncing.
    override fun getUpdatePacket(): Packet<ClientGamePacketListener?>? {
        // The packet uses the CompoundTag returned by #getUpdateTag. An alternative overload of #create exists
        // that allows you to specify a custom update tag, including the ability to omit data the client might not need.
        return ClientboundBlockEntityDataPacket.create(this)
    }

    // 简单的同步触发器
    fun syncData() {
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putFloat("currentYaw", currentYaw)
        tag.putFloat("targetYaw", targetYaw)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        this.currentYaw = tag.getFloat("currentYaw")
        this.targetYaw = tag.getFloat("targetYaw")
    }
}