package xyz.luobo.mindustry.common.entity.bullet

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MoverType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import xyz.luobo.mindustry.common.ModEntities

class ClientSideBullet(
    entityType: EntityType<*>,
    level: Level
) : Entity(ModEntities.DUO_BULLET_ENTITY.get(), level) {

    private var start: Vec3 = Vec3.ZERO
    private var end: Vec3 = Vec3.ZERO
    private var speed: Float = 0.0f
    private var life: Int = 0
    private var maxLife: Int = 200 // 默认生存时间

    constructor(
        level: Level,
        start: Vec3,
        end: Vec3,
        speed: Float
    ) : this(ModEntities.DUO_BULLET_ENTITY.get(), level) {
        this.start = start
        this.end = end
        this.speed = speed

        // 属性
        this.noPhysics = true
        this.isNoGravity = true
        this.setPos(start)

        // 计算方向
        val dir = end.subtract(start).normalize()
        deltaMovement = dir.scale(speed.toDouble())

        // 10秒后自动移除（安全保护）
        this.life = 0
        this.maxLife = 200
    }

//    override fun tick() {
//        // 性能优化：只执行必要的更新
//        this.life++
//
//        // 检查是否超过最大生存时间
//        if (life > maxLife) {
//            this.discard()
//            return
//        }
//
//        // 简化的移动逻辑，避免调用完整的super.tick()
//        moveBy(deltaMovement)
//
//        // 检查是否到达目标位置附近
//        val currentPos = position()
//        val distanceToTarget = currentPos.distanceTo(end)
//        if (distanceToTarget < 1.0) { // 如果足够接近目标点
//            this.discard()
//        }
//    }

    // 重写基础tick方法以跳过不必要的更新
    override fun baseTick() {
        // 减少更新操作，仅保留必要的
        this.life++

        // 检查是否超过最大生存时间
        if (life > maxLife) {
            this.discard()
            return
        }

        // 简化的移动更新
        move(MoverType.SELF, deltaMovement)

        // 检查是否到达目标位置附近
        val currentPos = position()
        val distanceToTarget = currentPos.distanceTo(end)
        if (distanceToTarget < 1.0) { // 如果足够接近目标点
            this.discard()
        }
    }

    // 跳过许多不必要的更新方法
    override fun handlePortal() {
        // 子弹不需要传送门逻辑
    }

    override fun updateInWaterStateAndDoFluidPushing(): Boolean {
        return false
    }

    override fun updateSwimming() {
        // 子弹不需要游泳逻辑
    }

    override fun lavaHurt() {
        // 子弹不需要岩浆伤害
    }

    override fun checkBelowWorld() {
        // 可选择性地保留或移除
        if (this.y < -64.0) {
            this.discard()
        }
    }

    // 优化设置标志的方法
    override fun setSharedFlagOnFire(flag: Boolean) {
        // 子弹不需要着火标记
    }

    override fun shouldBeSaved(): Boolean {
        return false
    }

    override fun fireImmune(): Boolean {
        return true
    }

    override fun defineSynchedData(p0: SynchedEntityData.Builder) {
    }

    override fun readAdditionalSaveData(p0: CompoundTag) {
    }

    override fun addAdditionalSaveData(p0: CompoundTag) {
    }

    companion object {
        // 工厂方法：仅在客户端创建
        fun spawn(world: Level, start: Vec3, end: Vec3, speed: Float) {
            if (world.isClientSide) {
                val bullet = ClientSideBullet(world, start, end, speed)
                world.addFreshEntity(bullet)
            }
        }
    }
}