package xyz.luobo.mturrets.core.combat

import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.FlyingMob
import net.minecraft.world.entity.monster.Blaze
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.shapes.CollisionContext
import org.joml.Quaternionf
import org.joml.Vector3f
import xyz.luobo.mturrets.common.ModEntities
import xyz.luobo.mturrets.core.MTurretsModBlockEntity
import xyz.luobo.mturrets.core.capability.impl.FluidCapabilityImpl
import xyz.luobo.mturrets.core.structure.Blueprint
import xyz.luobo.mturrets.core.structure.BlueprintAnchor
import xyz.luobo.mturrets.core.structure.BlueprintAnchorBlock
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * 新范式炮台锚点 BE(ADR-0009,#31):单层承载索敌/旋转/装填/开火管线、Magazine 单位账、
 * Coolant 内罐与 Health。
 *
 * 每 tick 时序(对位 ADR-0009):目标校验 → 装填累加(封顶 reload;Coolant 生效 ×1.5)→
 * 每 7t 索敌(Monster-only、最近优先)→ 旋转逼近目标角(rotateSpeed 封顶)→ 开火门
 * (装填满 ∧ 入 shootCone)→ 扳机扣 1 单位 + 造弹(各发共用扳机时刻瞄准角)→ curRecoil 衰减。
 * 装填与瞄准解耦:无目标照常装填。
 *
 * 同步(ADR-0005):yaw/pitch 低频 update tag + 单调开火计数器;瞬时角不上网;
 * curRecoil 是枪管动画的唯一逻辑量,客户端按开火计数器驱动后坐。
 */
abstract class TurretBE(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
    /** 炮台数值表(子类传入;构造器参数保证 magazine/health 初始化时已就绪)。 */
    val spec: TurretSpec
) : MTurretsModBlockEntity(type, pos, state), BlueprintAnchor {

    companion object {
        /** 索敌间隔(tick,#28 决议 7t)。 */
        const val TARGET_INTERVAL = 7

        /** Coolant 内罐容量(mB):一桶水恰好充满。 */
        const val WATER_TANK_CAPACITY = 1000

        /** 枪口高度(块内局部 y):与 barrel 部件齐平。 */
        const val MUZZLE_HEIGHT = 0.44

        /** 后坐衰减(每 tick 递减量)。 */
        const val RECOIL_DECAY = 0.1f

        /** 旋转同步节流(旋转变化最快每 2 tick 上报一次)。 */
        const val SYNC_THROTTLE = 2

        private const val SQRT2 = 1.41421356f

        /**
         * 发射/瞄准/旋转/可视化共用中心:2×2 起 = 锚点中心 + (size-1)/2 每水平轴(结构中心,#34)。
         * 纯函数(不触 level),使 #77 客户端调试可视化与服务端走同一份几何。
         */
        fun structureCenter(worldPosition: BlockPos, size: Int): Vec3 {
            val half = (size - 1) / 2f
            return worldPosition.center.add(half.toDouble(), 0.0, half.toDouble())
        }

        /**
         * 枪口点:结构中心沿 [toward] 的水平方向外推 [distance]、y = 结构中心 + MUZZLE_HEIGHT - 0.5。
         * 起点须落在自身结构方块之外的空气里,否则 clip 自命中 / 弹头出生即撞墙。
         */
        fun muzzlePoint(center: Vec3, toward: Vec3, distance: Double): Vec3 {
            val horizontal = Vec3(toward.x - center.x, 0.0, toward.z - center.z).normalize()
            return center.add(
                horizontal.x * distance,
                (MUZZLE_HEIGHT - 0.5).toDouble(),
                horizontal.z * distance
            )
        }

        /**
         * LOS 射线起点外推距离 = 结构外接圆半径(size/2·√2) + 0.25 间隙——必须超过角点半径,否则 2×2
         * 时对角方位的射线起点落在角成员方块内,clip 自命中 → LOS 恒 false(致盲);1×1 时 0.957 > 0.707 半对角。
         */
        fun losMuzzleDistance(size: Int): Double = (size / 2f) * SQRT2 + 0.25

        /** 弹头出生点外推距离(比 LOS 起点更靠内,LOS 起点须在其外以保证「可见 ⟹ 弹可及」)。 */
        fun fireMuzzleDistance(size: Int): Double = size / 2f + 0.25

        /**
         * 角度 → 单位方向。项目约定 yaw = atan2(dx, dz)(0 = +Z、正角向 +X),pitch 负 = 向上
         * (与 [pitchTowards] 产出及 Flywheel `rotateX` 消费一致)。
         */
        fun directionFromAngles(yaw: Float, pitch: Float): Vec3 {
            val yawRad = Math.toRadians(yaw.toDouble())
            val pitchRad = Math.toRadians(pitch.toDouble())
            val cosPitch = cos(pitchRad)
            return Vec3(sin(yawRad) * cosPitch, -sin(pitchRad), cos(yawRad) * cosPitch)
        }

        /** 瞄准/视线基准点:实体包围盒中心而非脚底(#68:旧值 eyeHeight*0.5 对僵尸仅 0.81,偏低)。 */
        fun aimCenter(entity: LivingEntity): Vec3 =
            entity.position().add(0.0, entity.boundingBox.getYsize() * 0.5, 0.0)

        /**
         * 空中单位判定:原版飞行怪抽象(FlyingMob)+ 恒无重力者(恼鬼)+ 烈焰人(重力学悬浮,最低空也悬停)。
         * 与高度/着地无关:不用 !onGround——低空悬停的恶魂 4×4 箱体贴地 → onGround=true,旧判据会误放弃(#53)。
         */
        fun isAirUnit(entity: LivingEntity): Boolean =
            entity is FlyingMob || entity.isNoGravity() || entity is Blaze

        /** 对空/对地过滤:该实体是否落入本炮台可锁类别(服务端索敌与 #77 可视化共用一份)。 */
        fun acceptsCategory(entity: LivingEntity, targetAir: Boolean, targetGround: Boolean): Boolean =
            (targetAir && isAirUnit(entity)) || (targetGround && !isAirUnit(entity))
    }

    override val currentBlueprint: Blueprint
        get() = (blockState.block as BlueprintAnchorBlock).blueprint
    /** 单位账弹仓(ADR-0009):按弹种分账、LIFO 选弹;自动化供弹经 [magazineHandler] 折算注入,不存物理物品。 */
    val magazine = Magazine(spec.maxAmmo)

    /** 供弹能力槽面(#73 自动化供弹):把标准 IItemHandler 翻译到 Magazine 单位账。 */
    val magazineHandler = MagazineItemHandler(this)

    /** 内罐只收水(Coolant;缺液只掉速不阻火,ADR-0009)。 */
    override val fluidCapability: FluidCapabilityImpl =
        createFluidCapability(
            capacity = WATER_TANK_CAPACITY,
            maxReceive = WATER_TANK_CAPACITY,
            maxExtract = 0,
            isValidFluid = { it.fluid == Fluids.WATER }
        )

    // ===== 运行状态(服务端权威;saveAdditional 持久化) =====

    /** 当前锁定目标(Monster-only)。 */
    var target: LivingEntity? = null
        private set

    /** 当前枪口偏航(度;0 = +Z)。 */
    var yaw: Float = 0f
        private set

    /** 当前枪口俯仰(度;负 = 向上)。 */
    var pitch: Float = 0f
        private set

    /** 单调开火计数器(客户端枪管动画消费;只增不减)。 */
    var fireCount: Long = 0
        private set

    /** 结构 Health(锚点单条;结算归 #34/共享骨架,本票只存档)。 */
    var health: Int = spec.health
        private set

    /** 后坐(0..1,枪管动画单一逻辑量)。 */
    var curRecoil: Float = 0f
        private set

    /** 目标偏航角(度;客户端 visual 按 rotateSpeed 向其逼近,ADR-0005 只发目标角)。 */
    var targetYaw: Float = 0f
        private set

    /** 目标俯仰角(度;负 = 向上)。 */
    var targetPitch: Float = 0f
        private set
    private var reloadCounter = 0f
    private var targetTimer = 0
    private var totalShots = 0L
    private var lastRotationSync = 0L
    // ===== 点射队列(#34 首次启用 shots>1/shotDelay>0) =====

    /** 待出膛队列发数(扳机已统一扣账;不持久化——存档丢队列对齐 Mindustry Time.run 语义)。 */
    private var burstRemaining = 0
    private var burstDelay = 0f

    /** 队列各发共用扳机时刻的瞄准方向与枪口(ADR-0009「各发共用扳机时刻瞄准角」)。 */
    private var burstDir = Vec3.ZERO
    private var burstMuzzle = Vec3.ZERO

    /** 队列弹种 = 扳机时刻的尾弹种(扣账后弹仓可能已变,队列不跟随)。 */
    private var burstType: BulletType? = null
    // ===== 枪口 FX(#62,纯客户端) =====
    /** 上次观测到的开火计数器(客户端 ticker 消费,跨 tick 比较)。 */
    private var lastMuzzleFire = 0L

    /** 弹种定义查询;非本炮台弹药返回 null。 */
    fun ammoTypeFor(item: net.minecraft.world.item.Item): AmmoType? =
        spec.ammoTypes.firstOrNull { it.item == item }

    /** 部分装弹:按剩余容量向下取整到整件,返回接受件数(0 = 整堆拒收,非本炮台弹药同为 0)。 */
    fun tryLoadAmmo(stack: ItemStack): Int {
        val ammo = ammoTypeFor(stack.item) ?: return 0
        return magazine.load(stack.item, stack.count, ammo.unitMultiplier)
    }

    override fun contentsToScatter(destroyed: Boolean): List<ItemStack> {
        if (destroyed) return emptyList()
        // 拆除折回:floor(单位/入仓倍率) 的物品(ADR-0009)
        return magazine.toItems()
    }

    // ===== 服务端 tick(由炮台方块 getTicker 排程) =====

    fun tickServer() {
        val lv = level ?: return

        if (target != null && !isValidTarget(target!!)) target = null

        // 每 7t 索敌(ADR-0009;首 tick 0%7==0 即找):Monster-only、射程内最近者优先
        if (targetTimer++ % TARGET_INTERVAL == 0) {
            findTarget(lv)
        }

        // 装填累加:封顶 reload;速率 = 尾弹种 reloadMultiplier × (Coolant 生效则 ×1.5)(ADR-0009,#34)
        val coolantActive = fluidCapability.currentFluid.amount >= spec.coolantPerShot
        val tailMult = magazine.tail?.let { tail ->
            spec.ammoTypes.firstOrNull { it.item == tail.item }?.bullet?.reloadMultiplier ?: 1f
        } ?: 1f
        val reloadSpeed = (if (coolantActive) spec.coolantReloadMultiplier else 1f) * tailMult
        if (reloadCounter < spec.reloadTicks) {
            reloadCounter = minOf(spec.reloadTicks, reloadCounter + reloadSpeed)
        }

        // 旋转逼近目标角(提前量瞄准点);无目标保持当前角
        val tgt = target
        if (tgt != null) {
            val aim = aimPoint(tgt)
            targetYaw = yawTowards(aim)
            targetPitch = pitchTowards(aim)
            val nextYaw = approachAngle(yaw, targetYaw, spec.rotateSpeed)
            val nextPitch = approachAngle(pitch, targetPitch, spec.rotateSpeed)
            val moved = abs(Mth.wrapDegrees(nextYaw - yaw)) > 0.001f
                || abs(nextPitch - pitch) > 0.001f
            yaw = nextYaw
            pitch = nextPitch
            if (moved && lv.gameTime - lastRotationSync >= SYNC_THROTTLE) {
                lastRotationSync = lv.gameTime
                syncData()
            }
        }

        // 开火门:装填满 ∧ 入锥角 ∧ 有弹
        if (tgt != null && reloadCounter >= spec.reloadTicks
            && abs(Mth.wrapDegrees(targetYaw - yaw)) < spec.shootCone
            && magazine.canFire()
        ) {
            fire(lv, tgt)
            reloadCounter = 0f
        }

        // 点射队列排程:队列 (shots-1) 发按 shotDelay 依次出膛;独立于目标存活(扳机已扣账),
        // 也不阻塞下一扳机(装填与队列不重叠:所有现行 spec 均 reload > (shots-1)*shotDelay)
        if (burstRemaining > 0) {
            burstDelay -= 1f
            if (burstDelay <= 0f) {
                burstRemaining--
                burstDelay = spec.shotDelay
                val queuedType = burstType
                if (queuedType != null) spawnBullet(lv, queuedType, burstMuzzle, burstDir)
            }
        }

        // 后坐衰减
        if (curRecoil > 0f) {
            curRecoil = maxOf(0f, curRecoil - RECOIL_DECAY)
            if (curRecoil == 0f) setChanged()
        }
    }

    // ===== 索敌 =====

    /**
     * 目标过滤(#43):只打 Monster(ADR-0009)+ 对空/对地类别([acceptsCategory])+ 射程内 + 有视线。
     * 谓词末位调用 hasLosTo:短求值保证 clip 只在通过廉价过滤的候选上跑。
     */
    private fun isValidTarget(entity: LivingEntity): Boolean =
        entity is Monster && entity.isAlive && !entity.isRemoved &&
            acceptsCategory(entity, spec.targetAir, spec.targetGround) &&
            entity.distanceToSqr(anchorCenter()) <= spec.range * spec.range &&
            hasLosTo(entity)

    private fun findTarget(lv: Level) {
        // 预筛选盒覆盖整个结构跨距(2×2 时锚点单格盒会漏掉 +x/+z 侧射程边缘目标)
        val area = AABB(worldPosition.center, worldPosition.offset(spec.size - 1, 0, spec.size - 1).center)
            .inflate(spec.range.toDouble())
        val candidates = lv.getEntitiesOfClass(LivingEntity::class.java, area) { isValidTarget(it) }
        target = candidates.minByOrNull { it.distanceToSqr(anchorCenter()) }
    }

    /**
     * 视线判定(#43):枪口([muzzleFor])→ 目标包围盒中心,只测方块碰撞形状(COLLIDER,与弹头 move 碰撞一致)、
     * 流体不挡、不测实体,与原版 [LivingEntity.hasLineOfSight] 同判据。起点用枪口外推点而非锚点中心
     * (否则 1×1 中心落在自身实心方块内自命中、2×2 对角落在角成员方块内,详见 [muzzleFor])。
     * 谓词末位调用:短求值保证 clip 只在通过阵营/存活/对空地/射程过滤的候选上跑。
     */
    private fun hasLosTo(entity: LivingEntity): Boolean {
        val lv = level ?: return false
        return lv.clip(
            ClipContext(
                muzzleFor(entity.position()),
                aimCenter(entity),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                CollisionContext.empty()
            )
        )
            ?.type == HitResult.Type.MISS
    }

    /** LOS 射线起点:结构中心朝目标外推 [losMuzzleDistance](几何见 companion,与 #77 可视化共用)。 */
    private fun muzzleFor(tgt: Vec3): Vec3 =
        muzzlePoint(anchorCenter(), tgt, losMuzzleDistance(spec.size))

    // ===== 旋转与瞄准 =====

    /** 发射/瞄准/旋转共用中心(几何见 companion [structureCenter])。 */
    private fun anchorCenter(): Vec3 = structureCenter(worldPosition, spec.size)

    /** 提前量瞄准点:对移动目标按弹速解命中时间外推位置(LeadCalculator 存活件);基准为包围盒中心(#68)。 */
    private fun aimPoint(tgt: LivingEntity): Vec3 {
        val look = aimCenter(tgt)
        val bullet = spec.ammoTypes.firstOrNull { it.item == magazine.tail?.item }?.bullet ?: return look
        val time = LeadCalculator.solveLeadEquation(
            look.subtract(anchorCenter()),
            tgt.deltaMovement,
            bullet.speed.toDouble()
        )
        return if (time > 0) look.add(tgt.deltaMovement.scale(time)) else look
    }

    private fun yawTowards(pos: Vec3): Float {
        val dx = pos.x - anchorCenter().x
        val dz = pos.z - anchorCenter().z
        // yaw 约定 0 = +Z、正角向 +X(与 muzzleFlash facing=(sin,0,cos) 及模型枪口同向);
        // 旧 atan2(dz,dx)-90° 反相 180°,致炮口视觉指向与目标相反(issue 66)
        return Math.toDegrees(atan2(dx, dz)).toFloat()
    }

    private fun pitchTowards(pos: Vec3): Float {
        val dx = pos.x - anchorCenter().x
        val dz = pos.z - anchorCenter().z
        val dy = pos.y - (worldPosition.y + MUZZLE_HEIGHT)
        val horizontal = sqrt(dx * dx + dz * dz)
        // 负 = 向上(visual 直接消费)
        return -Math.toDegrees(atan2(dy, horizontal)).toFloat()
    }

    /** 角度逼近:偏航走最短角(±180 环绕);俯仰封顶 ±90。 */
    private fun approachAngle(current: Float, target: Float, maxStep: Float): Float {
        val diff = Mth.wrapDegrees(target - current)
        val step = if (abs(diff) <= maxStep) diff else sign(diff) * maxStep
        return Mth.wrapDegrees(current + step)
    }

    // ===== 开火 =====

    private fun fire(lv: Level, tgt: LivingEntity) {
        val entry = magazine.tail ?: return
        val ammo = spec.ammoTypes.firstOrNull { it.item == entry.item } ?: return
        val aim = aimPoint(tgt)
        // 枪口:结构中心沿瞄准方向外推(几何见 companion [muzzlePoint],与 LOS/可视化共用),
        // 保证出生点在空气(不与自己方块碰撞)
        val muzzle = muzzlePoint(anchorCenter(), aim, fireMuzzleDistance(spec.size))
        var dir = aim.subtract(muzzle).normalize()
        dir = jitter(dir, spec.inaccuracy + ammo.bullet.inaccuracy, lv.random)

        // 点射排程:首发出膛 + 队列 (shots-1) 发,共用扳机时刻瞄准方向(ADR-0009;#34 首用)
        spawnBullet(lv, ammo.bullet, muzzle, dir)
        burstRemaining = spec.shots - 1
        burstDelay = if (burstRemaining > 0) spec.shotDelay else 0f
        burstDir = dir
        burstMuzzle = muzzle
        burstType = ammo.bullet

        // 扳机扣账(点射各发共用同一次扣账,ADR-0009)
        magazine.drainOne()
        if (fluidCapability.currentFluid.amount >= spec.coolantPerShot) {
            drainFluidInternal(spec.coolantPerShot)
        }
        totalShots++
        fireCount++
        curRecoil = 1f
        syncData() // 开火计数器脉冲即时上报(枪管动画)
    }

    private fun spawnBullet(lv: Level, type: BulletType, muzzle: Vec3, dir: Vec3) {
        val bullet = ModEntities.TURRET_BULLET.get().create(lv) ?: return
        bullet.moveTo(muzzle.x, muzzle.y, muzzle.z, 0f, 0f)
        bullet.init(type, dir)
        lv.addFreshEntity(bullet)
        // 枪声(#57):每发一次,服务端在枪口定位播放,音调 0.8~1.2 / 音量 0.9~1.0 随机抖动,
        // 对齐 Mindustry SoundEffect;spawnBullet 对首射与点射各发各调一次,天然是"每发"入口。
        val pitch = 0.8f + lv.random.nextFloat() * 0.4f
        val volume = 1f - lv.random.nextFloat() * 0.1f
        lv.playSound(null, muzzle.x, muzzle.y, muzzle.z, spec.shootSound.get(), net.minecraft.sounds.SoundSource.BLOCKS, volume, pitch)
    }

    /**
     * 枪口闪光(#62,纯客户端):开火计数器跳变即在枪口放一次 FLAME+SMOKE(原版粒子)。
     * 由客户端 BlockEntityTicker 每 tick 调用——Flywheel 渲染线程不能放粒子,故走 main-thread ticker。
     * 每扳机一次(Scatter 双发点射也只 1 次,对齐既有后坐节奏,见 #62 决策);枪口位置 = 当前 yaw/pitch
     * 的炮管口,与 Flywheel 后坐同源(零新增同步,ADR-0005 只发目标角+开火计数器)。
     */
    fun muzzleFlash() {
        val lv = level ?: return
        if (!lv.isClientSide) return
        val fire = fireCount
        if (fire == lastMuzzleFire) return
        lastMuzzleFire = fire
        // 枪口:结构中心沿当前 yaw 水平外推 + pitch 抬升(与 fire() 的 muzzle = center + facing×dist 同源)。
        // 项目约定 yaw = atan2(dx, dz)(见 syncDirection),故 facing = (sin(yaw), 0, cos(yaw));pitch 负=向上。
        val center = anchorCenter()
        val yawRad = (Mth.DEG_TO_RAD * yaw).toDouble()
        val pitchRad = (Mth.DEG_TO_RAD * pitch).toDouble()
        val dist = (spec.size / 2f + 0.25f).toDouble()
        val mz = center.add(
            Math.sin(yawRad) * dist,
            (MUZZLE_HEIGHT - 0.5).toDouble() + Math.sin(pitchRad) * dist,
            Math.cos(yawRad) * dist
        )
        lv.addParticle(ParticleTypes.FLAME, mz.x, mz.y, mz.z, 0.0, 0.05, 0.0)
        lv.addParticle(ParticleTypes.SMOKE, mz.x, mz.y, mz.z, 0.0, 0.05, 0.0)
        lv.addParticle(ParticleTypes.LARGE_SMOKE, mz.x, mz.y, mz.z, 0.0, 0.02, 0.0)
    }


    /** 出生散布:绕竖直轴与水平轴各转 ±inaccuracy(度内均匀随机)。 */
    private fun jitter(dir: Vec3, degrees: Float, random: net.minecraft.util.RandomSource): Vec3 {
        if (degrees <= 0f) return dir
        val yawJ = Math.toRadians(((random.nextFloat() * 2f - 1f) * degrees).toDouble()).toFloat()
        val pitchJ = Math.toRadians(((random.nextFloat() * 2f - 1f) * degrees).toDouble()).toFloat()
        val q = Quaternionf().rotateY(yawJ).rotateX(pitchJ)
        val v = Vector3f(dir.x.toFloat(), dir.y.toFloat(), dir.z.toFloat())
        q.transform(v)
        return Vec3(v.x.toDouble(), v.y.toDouble(), v.z.toDouble())
    }

    // ===== 存档与同步 =====

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        yaw = tag.getFloat("turret_yaw")
        pitch = tag.getFloat("turret_pitch")
        targetYaw = tag.getFloat("turret_target_yaw")
        targetPitch = tag.getFloat("turret_target_pitch")
        reloadCounter = tag.getFloat("turret_reload")
        totalShots = tag.getLong("turret_shots")
        fireCount = tag.getLong("turret_fire")
        health = tag.getInt("turret_health").coerceAtLeast(1)
        magazine.load(tag, registries)
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putFloat("turret_yaw", yaw)
        tag.putFloat("turret_pitch", pitch)
        tag.putFloat("turret_target_yaw", targetYaw)
        tag.putFloat("turret_target_pitch", targetPitch)
        tag.putFloat("turret_reload", reloadCounter)
        tag.putLong("turret_shots", totalShots)
        tag.putLong("turret_fire", fireCount)
        tag.putInt("turret_health", health)
        magazine.save(tag, registries)
    }

    override fun getUpdatePacket(): net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket =
        net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this)

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag =
        saveWithoutMetadata(registries)
}