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

    /** RGB 颜色 + 顶字节 lifetime(ADR-0010:客户端推导收缩进度;渲染只取 RGB,零新增同步字段) */
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
        // lifetime 顶字节打包进同步色(ADR-0010):渲染路径透明,≤255 的数据不变式
        require(bulletType.lifetime in 1..255) { "lifetime (${bulletType.lifetime}) must fit DATA_COLOR alpha byte" }
        damage = bulletType.damage
        maxLifetime = bulletType.lifetime
        gravity = bulletType.gravity
        bulletColor = bulletType.color or (bulletType.lifetime shl 24)
        bulletSize = bulletType.bulletSize
        splashDamage = bulletType.splashDamage
        splashRadius = bulletType.splashRadius
        fragCount = bulletType.fragCount
        fragBullet = bulletType.fragBullet
        graceTicks = grace
        deltaMovement = direction.scale(bulletType.speed.toDouble())
        syncDirection()
    }

    companion object {
        /** 同步色:int 低 24 位 RGB,顶字节打包 lifetime(ADR-0010,客户端收缩用)。 */
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

        // 扫掠盒 = 起止两盒并集:高速弹可整段跨过薄目标(ADR-0010);撞墙分支共用同一查询
        val sweep = boundingBox
        move(MoverType.SELF, deltaMovement)
        syncDirection()

        // 出生免撞期(碎片无碰撞飞行),结束后恢复碰撞结算
        if (graceTicks > 0) {
            graceTicks--
            return
        }

        // 撞方块即消失;撞敌对生物(只打 Monster)也消失——直击列表一次查询、两分支共用(ADR-0010)
        val direct = queryTargets(sweep.minmax(boundingBox).inflate(0.15))
        if (horizontalCollision || verticalCollision || direct.isNotEmpty()) {
            val origin = if (horizontalCollision || verticalCollision) position()
            else entryOrigin(sweep.center, boundingBox.center, direct) ?: position()
            impact(direct, origin)
        }
    }

    /** 命中结算:直击 → 溅射 → 破片,随后消失(直击与溅射独立结算,ADR-0006/#34;origin=命中点)。 */
    private fun impact(direct: List<LivingEntity>, origin: Vec3) {
        val lv = level()
        // 直击:命中列表由 tick 一次性扫掠查询(ADR-0010),不再二次查询;阵营过滤见查询处(ADR-0009)
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
            val radius = splashRadius.toDouble()
            val area = lv.getEntities(this, AABB(origin, origin).inflate(radius)) { entity ->
                entity is LivingEntity && entity is Monster && entity.isAlive
            }
            for (target in area) {
                if (!target.isAlive) continue
                val distSqr = target.distanceToSqr(origin)
                if (distSqr <= radius * radius) {
                    val falloff = 0.4f + 0.6f * (1f - (Math.sqrt(distSqr) / radius).toFloat())
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
                bullet.moveTo(origin.x, origin.y, origin.z, 0f, 0f)
                bullet.init(frag, dir, grace = FRAG_GRACE)
                lv.addFreshEntity(bullet)
            }
        }

        discard()
    }

    /** 朝向同步:yRot/xRot 携带飞行方向,客户端视线轴滚转对齐用(原版 move 包同步,零新增字段)。 */
    private fun syncDirection() {
        val dx = deltaMovement.x
        val dy = deltaMovement.y
        val dz = deltaMovement.z
        val horizontal = Math.hypot(dx, dz)
        setYRot(Math.toDegrees(Math.atan2(dx, dz)).toFloat())
        setXRot(Math.toDegrees(Math.atan2(dy, horizontal)).toFloat())
    }

    /** 敌对生物查询(ADR-0009 阵营过滤):直击候选,一次查询供撞墙/撞怪两分支共用(ADR-0010)。 */
    private fun queryTargets(box: AABB): List<LivingEntity> =
        level().getEntities(this, box) { entity ->
            entity is LivingEntity && entity is Monster && entity.isAlive
        }.filterIsInstance<LivingEntity>()

    /** 路径上最早被穿过的候选 → 命中点(slab 进入点);无候选穿越返回 null。 */
    private fun entryOrigin(from: Vec3, to: Vec3, targets: List<LivingEntity>): Vec3? {
        val delta = to.subtract(from)
        if (delta.lengthSqr() == 0.0) return null
        var earliest: Double? = null
        for (target in targets) {
            val t = rayBoxEntry(from, delta, target.boundingBox.inflate(0.15)) ?: continue
            if (earliest == null || t < earliest) earliest = t
        }
        return earliest?.let { from.add(delta.scale(it)) }
    }

    /** 段-盒 slab 测试:射线 p + t·d 首次进入 box 的参数 t,无交返回 null(ADR-0010)。 */
    private fun rayBoxEntry(p: Vec3, d: Vec3, box: AABB): Double? {
        var tMin = 0.0
        var tMax = 1.0
        val origin = doubleArrayOf(p.x, p.y, p.z)
        val dir = doubleArrayOf(d.x, d.y, d.z)
        val lo = doubleArrayOf(box.minX, box.minY, box.minZ)
        val hi = doubleArrayOf(box.maxX, box.maxY, box.maxZ)
        for (i in 0..2) {
            if (dir[i] == 0.0) {
                // 轴方向无位移:起点须在本轴区间内
                if (origin[i] < lo[i] || origin[i] > hi[i]) return null
            } else {
                var t1 = (lo[i] - origin[i]) / dir[i]
                var t2 = (hi[i] - origin[i]) / dir[i]
                if (t1 > t2) {
                    val t = t1
                    t1 = t2
                    t2 = t
                }
                tMin = maxOf(tMin, t1)
                tMax = minOf(tMax, t2)
                if (tMin > tMax) return null
            }
        }
        return tMin
    }
}