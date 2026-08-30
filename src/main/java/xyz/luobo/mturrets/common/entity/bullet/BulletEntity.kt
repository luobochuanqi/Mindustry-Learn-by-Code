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
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import xyz.luobo.mturrets.common.ModEntities
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
    // 溅射/破片(ADR-0006,#34):服务端结算数据,客户端渲染不需要
    private var splashDamage: Float = 0f
    private var splashRadius: Float = 0f
    private var fragCount: Int = 0
    private var fragBullet: BulletType? = null
    /** 出生免撞期(tick):碎片在命中点生成,仍在原目标箱体内,头几 tick 不结算碰撞以免原地自消(#34)。 */
    private var graceTicks = 0

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
     * [grace] 为出生免撞期:碎片在命中点生成时仍被原目标包围,短暂免撞避免瞬间自消。
     */
    fun init(bulletType: BulletType, direction: Vec3, grace: Int = 0) {
        damage = bulletType.damage
        maxLifetime = bulletType.lifetime
        gravity = bulletType.gravity
        bulletColor = bulletType.color
        bulletSize = bulletType.bulletSize
        splashDamage = bulletType.splashDamage
        splashRadius = bulletType.splashRadius
        fragCount = bulletType.fragCount
        fragBullet = bulletType.fragBullet
        graceTicks = grace
        deltaMovement = direction.scale(bulletType.speed.toDouble())
    }

    companion object {
        val DATA_COLOR = SynchedEntityData.defineId(BulletEntity::class.java, EntityDataSerializers.INT)
        val DATA_SIZE = SynchedEntityData.defineId(BulletEntity::class.java, EntityDataSerializers.FLOAT)
        /** 碎片出生免撞时长(2 tick ≈ 2.25 格,足够冲出 4×4 目标的 2 格半径)。 */
        const val FRAG_GRACE = 2
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

        // 出生免撞期(碎片无碰撞飞行),结束后恢复碰撞结算
        if (graceTicks > 0) {
            graceTicks--
            return
        }

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

    /** 命中结算:直击 → 溅射 → 破片,随后消失(直击与溅射独立结算,ADR-0006/#34)。 */
    private fun impact() {
        val lv = level()
        val pos = position()
        // 直击:只对重叠区内的敌对生物结算(ADR-0009 阵营过滤)
        val direct = lv.getEntities(this, boundingBox.inflate(0.15)) { entity ->
            entity is LivingEntity && entity is Monster && entity.isAlive
        }
        for (target in direct) {
            if (!target.isAlive) continue
            target.hurt(lv.damageSources().mobAttack(null), damage)
        }

        // 原版 hurt 落 10t 无敌帧:同一弹的直击会吞掉紧随的溅射结算,违反「直击+溅射独立结算」;
        // 溅射前清掉刚被打目标的免疫帧(只影响同 tick 的第二次结算,跨弹命中节奏不变)
        for (target in direct) {
            target.invulnerableTime = 0
        }

        // 溅射:命中点对 Monster 半径内独立结算,线性衰减 中心 100% → 边缘 40%(Mindustry 公式,#34)
        if (splashDamage > 0f && splashRadius > 0f) {
            val area = lv.getEntities(this, AABB(pos, pos).inflate(splashRadius.toDouble())) { entity ->
                entity is LivingEntity && entity is Monster && entity.isAlive
            }
            for (target in area) {
                if (!target.isAlive) continue
                val dist = target.distanceTo(this)
                if (dist <= splashRadius) {
                    val falloff = 0.4f + 0.6f * (1f - dist / splashRadius)
                    target.hurt(lv.damageSources().mobAttack(null), splashDamage * falloff)
                }
            }
        }

        // 破片:命中点按随机水平方向生成(ADR-0006 分裂语义,#34;碎片为自身 BulletType,可再溅射/不再分裂)
        val frag = fragBullet
        if (fragCount > 0 && frag != null) {
            repeat(fragCount) {
                val yaw = lv.random.nextFloat() * (Math.PI.toFloat() * 2f)
                val dir = Vec3(Math.sin(yaw.toDouble()), 0.0, Math.cos(yaw.toDouble()))
                val bullet = ModEntities.TURRET_BULLET.get().create(lv) ?: return@repeat
                bullet.moveTo(pos.x, pos.y, pos.z, 0f, 0f)
                bullet.init(frag, dir, grace = FRAG_GRACE)
                lv.addFreshEntity(bullet)
            }
        }

        discard()
    }
}