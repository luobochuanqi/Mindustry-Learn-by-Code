package xyz.luobo.mindustry.core.turret.entity

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import xyz.luobo.mindustry.core.MindustryModBlockEntity
import xyz.luobo.mindustry.core.turret.config.TurretConfig
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * 炮台方块实体最基础类
 * 处理目标查找、旋转、基础攻击逻辑
 * 模仿 Mindustry 的 TurretBuild 类
 *
 * @param type BlockEntityType
 * @param pos 方块位置
 * @param state 方块状态
 */
abstract class BaseTurretBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : MindustryModBlockEntity(type, pos, state) {

    // ========== 配置（子类必须实现）==========
    abstract val config: TurretConfig

    // ========== 旋转状态 ==========
    /** 目标旋转角度（偏航，度） */
    var targetRotation: Float = 0f
        protected set

    /** 当前实际旋转角度（偏航，度） */
    var currentRotation: Float = 0f
        protected set

    /** 目标俯仰角度（度） */
    var targetPitch: Float = 0f
        protected set

    /** 当前实际俯仰角度（度） */
    var currentPitch: Float = 0f
        protected set

    // ========== 目标 ==========
    /** 当前锁定的目标 */
    var currentTarget: LivingEntity? = null
        protected set

    /** 目标查找计时器 */
    private var targetTimer: Int = 0

    // ========== 资源状态 ==========
    /** 当前弹药数量 */
    var currentAmmo: Int = 0
        protected set

    /** 是否处于液体冷却状态 */
    var isCoolantActive: Boolean = false
        protected set

    /** 冷却状态剩余时间 */
    var coolantTimer: Int = 0
        protected set

    // ========== Tick 主循环 ==========

    /**
     * 服务端 Tick 主逻辑
     * 子类应重写此方法并在最后调用 super.tickServer()
     */
    open fun tickServer(level: Level, pos: BlockPos, state: BlockState) {
        // 更新冷却状态
        updateCoolant()

        // 每 10 tick 更新一次目标
        if (targetTimer++ >= 10) {
            targetTimer = 0
            updateTarget(level, pos)
        }

        // 更新旋转
        updateRotation()

        // 尝试射击
        tryShoot(level, pos)
    }

    // ========== 索敌逻辑 ==========

    /**
     * 更新目标
     */
    protected open fun updateTarget(level: Level, pos: BlockPos) {
        if (currentTarget == null || !isValidTarget(currentTarget)) {
            currentTarget = findTarget(level, pos)
        }
    }

    /**
     * 查找目标
     */
    protected open fun findTarget(level: Level, pos: BlockPos): LivingEntity? {
        val range = config.range.toDouble()
        val area = AABB(pos).inflate(range)

        val entities = level.getEntitiesOfClass(
            LivingEntity::class.java,
            area
        ) { entity -> isValidTarget(entity) }

        return selectTarget(entities, pos)
    }

    /**
     * 检查目标是否有效
     */
    protected open fun isValidTarget(entity: LivingEntity?): Boolean {
        if (entity == null || !entity.isAlive) return false

        // 检查是否为可攻击类型
        val isHostile = entity is Monster || entity is Player
        if (!isHostile) return false

        // 检查目标类型（空中/地面）
        val isFlying = !entity.onGround()
        return when {
            isFlying && !config.targetAir -> false
            !isFlying && !config.targetGround -> false
            else -> true
        }
    }

    /**
     * 选择目标（默认选择最近的）
     * 子类可以重写以实现不同的选择策略
     */
    protected open fun selectTarget(
        entities: List<LivingEntity>,
        pos: BlockPos
    ): LivingEntity? {
        return entities.minByOrNull { it.distanceToSqr(pos.center) }
    }

    // ========== 旋转控制 ==========

    /**
     * 更新旋转（平滑插值）
     */
    protected open fun updateRotation() {
        currentTarget?.let { target ->
            calculateAimAngles(target)
        }

        // 平滑偏航角
        val yawDiff = Mth.wrapDegrees(targetRotation - currentRotation)
        val maxRotation = config.rotateSpeed

        currentRotation += when {
            abs(yawDiff) > maxRotation -> sign(yawDiff) * maxRotation
            else -> yawDiff
        }
        currentRotation = Mth.wrapDegrees(currentRotation)

        // 平滑俯仰角
        val pitchDiff = targetPitch - currentPitch
        currentPitch += when {
            abs(pitchDiff) > maxRotation -> sign(pitchDiff) * maxRotation
            else -> pitchDiff
        }
        currentPitch = currentPitch.coerceIn(-90f, 90f)

        // 如果旋转有变化，同步到客户端
        if (abs(yawDiff) > 0.1f || abs(pitchDiff) > 0.1f) {
            setChanged()
            if (!level!!.isClientSide) {
                syncData()
            }
        }
    }

    /**
     * 计算瞄准角度
     */
    protected open fun calculateAimAngles(target: LivingEntity) {
        val targetPos = target.position()
        val turretPos = worldPosition.center

        val dx = targetPos.x - turretPos.x
        val dy = targetPos.y - turretPos.y
        val dz = targetPos.z - turretPos.z

        // 计算偏航角（水平旋转）
        targetRotation = (Math.toDegrees(atan2(dz, dx)) - 90).toFloat()

        // 计算俯仰角（垂直旋转）
        val horizontalDist = sqrt(dx * dx + dz * dz)
        targetPitch = -Math.toDegrees(atan2(dy, horizontalDist)).toFloat()
    }

    // ========== 攻击接口 ==========

    /**
     * 尝试射击（子类必须实现）
     */
    protected abstract fun tryShoot(level: Level, pos: BlockPos)

    /**
     * 发射投射物（子类实现）
     */
    protected abstract fun fireProjectile(level: Level, pos: BlockPos, target: LivingEntity)

    /**
     * 检查是否可以攻击（角度对准）
     */
    protected open fun canAttack(): Boolean {
        val yawDiff = abs(Mth.wrapDegrees(targetRotation - currentRotation))
        return yawDiff <= config.shootCone
    }

    /**
     * 检查是否可以射击
     * 子类可以重写此方法添加额外的射击条件
     */
    protected open fun canShoot(): Boolean {
        return hasAmmo()
    }

    /**
     * 检查是否有弹药
     */
    protected open fun hasAmmo(): Boolean = currentAmmo > 0

    /**
     * 消耗弹药
     */
    protected open fun consumeAmmo() {
        currentAmmo -= config.ammoPerShot
        if (currentAmmo < 0) currentAmmo = 0
        setChanged()
    }

    /**
     * 添加弹药
     */
    open fun addAmmo(amount: Int): Int {
        val space = config.maxAmmo - currentAmmo
        val toAdd = minOf(amount, space)
        currentAmmo += toAdd
        setChanged()
        return toAdd
    }

    // ========== 冷却系统 ==========

    /**
     * 更新冷却状态
     */
    protected open fun updateCoolant() {
        if (isCoolantActive && coolantTimer-- <= 0) {
            isCoolantActive = false
            setChanged()
        }
    }

    /**
     * 激活冷却
     */
    open fun activateCoolant(duration: Int) {
        isCoolantActive = true
        coolantTimer = duration
        setChanged()
    }

    /**
     * 获取装填速度倍率
     */
    protected open fun getReloadMultiplier(): Float {
        return if (isCoolantActive) config.coolantMultiplier else 1f
    }

    // ========== 网络同步 ==========

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        return saveWithoutMetadata(registries)
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener?>? {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    // ========== 数据保存 ==========

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putFloat("targetRotation", targetRotation)
        tag.putFloat("currentRotation", currentRotation)
        tag.putFloat("targetPitch", targetPitch)
        tag.putFloat("currentPitch", currentPitch)
        tag.putInt("currentAmmo", currentAmmo)
        tag.putBoolean("isCoolantActive", isCoolantActive)
        tag.putInt("coolantTimer", coolantTimer)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        targetRotation = tag.getFloat("targetRotation")
        currentRotation = tag.getFloat("currentRotation")
        targetPitch = tag.getFloat("targetPitch")
        currentPitch = tag.getFloat("currentPitch")
        currentAmmo = tag.getInt("currentAmmo")
        isCoolantActive = tag.getBoolean("isCoolantActive")
        coolantTimer = tag.getInt("coolantTimer")
    }

    // ========== 便捷方法 ==========

    /**
     * 获取炮台中心位置
     */
    protected fun getTurretCenter(): Vec3 {
        return worldPosition.center.add(0.0, 0.5, 0.0)
    }

    /**
     * 获取射击位置
     */
    protected open fun getShootPos(): Vec3 {
        return getTurretCenter()
    }
}