package xyz.luobo.mturrets.common.entity.bullet

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import xyz.luobo.mturrets.core.turret.bullet.BulletType
import java.util.UUID
import kotlin.math.pow

/**
 * 服务端子弹实体
 * 由炮台发射的真实投射物:携带 [BulletType] 的属性飞行,
 * 命中敌对生物造成伤害,支持穿透与范围伤害。
 *
 * 客户端不做运动模拟,位置由服务端同步。
 */
class TurretBulletEntity(
    entityType: EntityType<*>,
    level: Level
) : Entity(entityType, level) {

    // ========== 子弹属性(服务端权威) ==========

    /** 单发伤害 */
    private var damage: Float = 0f

    /** 存活时间上限(tick) */
    private var maxLifetime: Int = 60

    /** RGB 颜色(同步给客户端渲染) */
    var bulletColor: Int
        get() = entityData.get(DATA_COLOR)
        private set(value) {
            entityData.set(DATA_COLOR, value)
        }

    /** 视觉大小(同步给客户端渲染) */
    var bulletSize: Float
        get() = entityData.get(DATA_SIZE)
        private set(value) {
            entityData.set(DATA_SIZE, value)
        }

    /** 是否穿透 */
    private var pierce: Boolean = false

    /** 剩余可穿透数量(-1 无限) */
    private var pierceRemaining: Int = 0

    /** 穿透后的伤害衰减系数 */
    private var pierceDamageFactor: Float = 1f

    /** 已穿透次数 */
    private var pierceCount: Int = 0

    /** 重力影响(0 无重力) */
    private var gravity: Float = 0f

    /** 范围伤害 */
    private var splashDamage: Float = 0f

    /** 范围伤害半径 */
    private var splashRadius: Float = 0f

    /** 是否碰撞方块 */
    private var collidesWall: Boolean = true

    /** 开火炮台的所有者(用于伤害来源) */
    private var ownerId: UUID? = null

    // ========== 初始化 ==========

    /**
     * 用 [BulletType] 配置子弹
     * @param direction 发射方向(已归一化,含提前量与散布)
     * @param ownerId 炮台归属(可空,用于伤害来源)
     */
    fun init(bulletType: BulletType, direction: net.minecraft.world.phys.Vec3, ownerId: UUID?) {
        damage = bulletType.damage
        maxLifetime = bulletType.lifetime
        bulletColor = bulletType.color
        bulletSize = bulletType.bulletSize
        pierce = bulletType.pierce
        pierceRemaining = bulletType.pierceCap
        pierceDamageFactor = bulletType.pierceDamageFactor
        gravity = bulletType.gravity
        splashDamage = bulletType.splashDamage
        splashRadius = bulletType.splashRadius
        collidesWall = bulletType.collidesWall
        this.ownerId = ownerId

        val speed = if (bulletType.speed > 0f) bulletType.speed.toDouble() else 1.0
        deltaMovement = direction.scale(speed)
    }

    // ========== 实体数据 ==========

    companion object {
        val DATA_COLOR = SynchedEntityData.defineId(TurretBulletEntity::class.java, EntityDataSerializers.INT)
        val DATA_SIZE = SynchedEntityData.defineId(TurretBulletEntity::class.java, EntityDataSerializers.FLOAT)
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(DATA_COLOR, 0xFFFFFF)
        builder.define(DATA_SIZE, 1f)
    }

    // ========== 持久化(子弹生命周期短,无需保存内部状态) ==========

    override fun addAdditionalSaveData(tag: CompoundTag) {
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
    }

    // ========== 每 tick 逻辑(服务端) ==========

    override fun tick() {
        super.tick()

        if (level().isClientSide) return

        if (tickCount >= maxLifetime || position().y < level().minBuildHeight - 16) {
            discard()
            return
        }

        // 重力
        if (gravity > 0f) {
            deltaMovement = deltaMovement.add(0.0, -0.04 * gravity, 0.0)
        }

        // 移动(含方块碰撞检测)
        move(MoverType.SELF, deltaMovement)

        // 撞击方块
        if (collidesWall && (horizontalCollision || verticalCollision)) {
            impact(position())
            return
        }

        // 撞击生物
        val hits = level().getEntities(this, boundingBox.inflate(0.15)) { entity ->
            entity is LivingEntity && isTarget(entity)
        }
        if (hits.isNotEmpty()) {
            impact(hits.first().position())
        }
    }

    // ========== 命中处理 ==========

    /**
     * 目标过滤:与炮台索敌一致,只攻击敌对生物
     */
    private fun isTarget(entity: LivingEntity): Boolean {
        if (!entity.isAlive) return false
        return entity is Monster || entity is Player
    }

    /**
     * 命中点处理:造成伤害,按穿透/范围伤害结算
     */
    private fun impact(at: net.minecraft.world.phys.Vec3) {
        // 对位置上的所有目标造成伤害(支持穿透多个实体)
        val targets = level().getEntities(this, boundingBox.inflate(0.15)) { entity ->
            entity is LivingEntity && isTarget(entity)
        }

        for (target in targets) {
            if (!target.isAlive) continue

            val actualDamage = damage * pierceDamageFactor.toDouble().pow(pierceCount).toFloat()
            target.hurt(level().damageSources().mobAttack(null), actualDamage)

            if (pierce && pierceRemaining != 0) {
                pierceCount++
                if (pierceRemaining > 0) {
                    pierceRemaining--
                }
                // 穿透:继续飞行,不消失
                if (pierceRemaining != 0) {
                    continue
                }
            }
            break
        }

        if (splashDamage > 0f && splashRadius > 0f) {
            applySplash(at)
        }

        discard()
    }

    /**
     * 范围伤害(距离衰减)
     */
    private fun applySplash(center: net.minecraft.world.phys.Vec3) {
        val area = net.minecraft.world.phys.AABB(center, center).inflate(splashRadius.toDouble())
        val entities = level().getEntitiesOfClass(LivingEntity::class.java, area) { it.isAlive }
        for (entity in entities) {
            val distance = entity.position().distanceTo(center)
            if (distance > splashRadius.toDouble()) continue
            val factor = 1f - (distance / splashRadius.toDouble()).toFloat()
            entity.hurt(level().damageSources().mobAttack(null), splashDamage * factor)
        }
    }
}