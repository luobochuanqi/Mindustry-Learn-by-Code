package xyz.luobo.mindustry.common.turrets.duo

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
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


class DuoBE(
    pos: BlockPos,
    blockState: BlockState
) : BlockEntity(ModBlockEntityTypes.DUO_Block_Entity.get(), pos, blockState), GeoBlockEntity {
    private val animatableInstanceCache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)
    val DEPLOY_ANIM: RawAnimation = RawAnimation.begin().thenPlay("animation")

    var targetYaw: Float = 0f      // 目标方向
    var currentYaw: Float = 0f     // 当前实际方向

    companion object {
        const val ROTATION_SPEED = 40.0f // 每秒最大旋转角度（度）
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
        ) { e -> e.isAlive && e is Player }

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