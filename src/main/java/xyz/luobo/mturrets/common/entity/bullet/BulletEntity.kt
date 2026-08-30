package xyz.luobo.mturrets.common.entity.bullet

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import xyz.luobo.mturrets.core.combat.BulletType

/**
 * 通用飞行弹实体(ADR-0006/0009):单一实体承载全部飞行弹家族,行为差异全在 [BulletType] 数据对象。
 * 吃服务端权威运动(区块/碰撞/存档复用原版实体);客户端不做运动模拟,颜色/大小经同步数据渲染。
 *
 * 只攻击 Monster(ADR-0009 阵营过滤,不伤友好生物与玩家);命中即消失(despawnOnHit,
 * 本期无穿透/分裂);寿命走 [BulletType.lifetime],超时消失。瞬时家族(闪电/激光/光束)按
 * ADR-0006 零实体原则另走射线判定,不注册本实体。
 */
class BulletEntity(entityType: EntityType<*>, level: Level) : Entity(entityType, level) {

    private var damage: Float = 0f
    private var maxLifetime: Int = 20
    private var gravity: Float = 0f

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

    /**
     * 服务端初始化:以炮台校准后的方向(已含提前量与散布)发射。
     */
    fun init(bulletType: BulletType, direction: Vec3) {
        damage = bulletType.damage
        maxLifetime = bulletType.lifetime
        gravity = bulletType.gravity
        bulletColor = bulletType.color
        bulletSize = bulletType.bulletSize
        deltaMovement = direction.scale(bulletType.speed.toDouble())
    }

    companion object {
        val DATA_COLOR = SynchedEntityData.defineId(BulletEntity::class.java, EntityDataSerializers.INT)
        val DATA_SIZE = SynchedEntityData.defineId(BulletEntity::class.java, EntityDataSerializers.FLOAT)
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(DATA_COLOR, 0xFFFFFF)
        builder.define(DATA_SIZE, 0.5f)
    }

    // 子弹生命周期短,状态全部初始化时注入,不持久化
    override fun addAdditionalSaveData(tag: CompoundTag) {}
    override fun readAdditionalSaveData(tag: CompoundTag) {}

    override fun tick() {
        super.tick()
        if (level().isClientSide) return

        if (tickCount >= maxLifetime || position().y < level().minBuildHeight - 16) {
            discard()
            return
        }

        // 重力近似(BulletType.gravity > 0 时启用)
        if (gravity > 0f) {
            deltaMovement = deltaMovement.add(0.0, -0.04 * gravity, 0.0)
        }

        move(MoverType.SELF, deltaMovement)

        // 撞方块即消失
        if (horizontalCollision || verticalCollision) {
            impact()
            return
        }

        // 撞敌对生物(只打 Monster)
        val hit = level().getEntities(this, boundingBox.inflate(0.15)) { entity ->
            entity is LivingEntity && entity is Monster && entity.isAlive
        }.firstOrNull()
        if (hit != null) {
            impact()
        }
    }

    /** 命中结算:对重叠区的敌对生物各扣一次伤害,随后消失。 */
    private fun impact() {
        val targets = level().getEntities(this, boundingBox.inflate(0.15)) { entity ->
            entity is LivingEntity && entity is Monster && entity.isAlive
        }
        for (target in targets) {
            if (!target.isAlive) continue
            target.hurt(level().damageSources().mobAttack(null), damage)
        }
        discard()
    }
}