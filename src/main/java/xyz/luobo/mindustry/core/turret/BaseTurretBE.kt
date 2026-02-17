package xyz.luobo.mindustry.core.turret

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import xyz.luobo.mindustry.core.ModBlockEntity
import xyz.luobo.mindustry.core.capability.IEnergyCapability
import xyz.luobo.mindustry.core.capability.IFluidCapability
import xyz.luobo.mindustry.core.capability.IItemCapability
import xyz.luobo.mindustry.core.capability.impl.EnergyCapabilityImpl
import xyz.luobo.mindustry.core.capability.impl.FluidCapabilityImpl
import xyz.luobo.mindustry.core.turret.ammo.AmmoStats
import xyz.luobo.mindustry.core.turret.laser.LaserStats
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sign

/**
 * 炮台方块实体基类
 * 使用组合方式管理 Capability（能量、液体、物品）
 * 提供目标追踪、旋转控制、攻击冷却等通用功能
 * 支持完整的炮台配置系统（弹药、液体、强化等）
 */
abstract class BaseTurretBE(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : ModBlockEntity(type, pos, state) {

    // ========== 配置系统 ==========

    /**
     * 炮台配置
     * 子类必须实现此方法提供炮台的配置
     */
    abstract val config: TurretConfig

    // ========== Capability 配置 ==========

    /**
     * 能量 Capability（激光炮台使用）
     */
    override val energyCapability: EnergyCapabilityImpl? by lazy {
        if (config.hasEnergySupport) {
            createEnergyCapability(
                capacity = config.energyCapacity,
                maxReceive = config.energyConsumptionPerSecond.toInt() / 20,
                maxExtract = 0
            )
        } else {
            null
        }
    }

    /**
     * 液体 Capability（液体强化使用）
     */
    override val fluidCapability: FluidCapabilityImpl? by lazy {
        if (config.hasLiquidSupport) {
            createFluidCapability(
                capacity = config.liquidCapacity,
                maxReceive = 100,
                maxExtract = 0
            )
        } else {
            null
        }
    }

    /**
     * 物品 Capability（弹药炮台使用）
     */
    override val itemCapability: IItemCapability? = null // 弹药炮台不使用标准物品槽

    // ========== 旋转系统 ==========

    /** 目标方向（偏航角） */
    var targetYaw: Float = 0f

    /** 当前实际方向（偏航角） */
    var currentYaw: Float = 0f

    /** 每秒最大旋转角度（度） */
    abstract val rotationSpeed: Float

    /** 旋转角度容差（度），在此范围内认为已对准目标 */
    open val rotationTolerance: Float = 30f

    // ========== 攻击系统 ==========

    /** 攻击冷却时间（ticks） */
    var attackCooldown: Int = 0

    /** 当前实际冷却时间（ticks），考虑弹药和液体的影响 */
    var actualCooldown: Int = 0

    /** 基础冷却时间（ticks），从配置计算 */
    val baseCooldown: Int
        get() = config.cooldownTicks

    /** 当前目标实体 */
    protected var currentTarget: LivingEntity? = null

    // ========== 弹药系统 ==========

    /** 当前弹药数量 */
    var currentAmmo: Int = 0

    /** 当前使用的弹药 */
    var currentAmmoStats: AmmoStats? = null

    // ========== 液体系统 ==========

    /** 当前是否强化状态 */
    var isBoosted: Boolean = false

    /** 当前强化剩余时间（ticks） */
    var boostRemainingTicks: Int = 0

    // ========== 激光系统 ==========

    /** 激光持续伤害计时器（ticks） */
    var laserDamageTimer: Int = 0

    // ========== 便捷访问 ==========

    /**
     * 能量便捷访问（激光炮台使用）
     */
    protected val energy: IEnergyCapability?
        get() = energyCapability

    /**
     * 液体便捷访问（液体强化使用）
     */
    protected val fluid: IFluidCapability?
        get() = fluidCapability

    // ========== Tick 逻辑 ==========

    /**
     * 服务端 Tick 主逻辑
     * 子类应重写此方法或调用 super.tickServer() 后添加自定义逻辑
     */
    open fun tickServer(level: Level, pos: BlockPos, state: BlockState) {
        // 1. 更新强化状态
        updateBoostStatus()

        // 2. 激光炮台：消耗电力并造成持续伤害
        if (config.isLaserTurret) {
            updateLaserLogic(level, pos)
        }

        // 3. 计算实际冷却时间（考虑弹药和液体影响）
        updateActualCooldown()

        // 4. 查找目标
        currentTarget = findNearestTarget(level, pos, config.range)

        // 5. 更新目标方向
        if (currentTarget != null) {
            targetYaw = calculateYawToTarget(pos, currentTarget!!.onPos)
        }

        // 6. 平滑旋转
        updateRotation()

        // 7. 攻击逻辑（弹药炮台）
        if (config.isAmmoTurret) {
            updateAttackLogic(level, pos)
        }
    }

    /**
     * 更新激光逻辑
     * 激光炮台持续消耗电力并造成伤害
     */
    protected open fun updateLaserLogic(level: Level, pos: BlockPos) {
        val energyCap = energy ?: return

        // 检查是否有足够的电力
        val energyNeeded = (config.energyConsumptionPerSecond / 20f).toInt() // 每tick 消耗

        if (energyCap.hasEnergy(energyNeeded)) {
            // 消耗电力
            energyCap.extractEnergy(energyNeeded, false)

            // 如果有目标，造成伤害
            if (currentTarget != null && canAttack()) {
                laserDamageTimer++
                if (laserDamageTimer >= 20) { // 每秒造成一次伤害
                    laserDamageTimer = 0
                    val damage = config.laserStats.damagePerSecond
                    currentTarget!!.hurt(level.damageSources().magic(), damage)
                }
            } else {
                laserDamageTimer = 0
            }

            setChanged()
        }
    }

    /**
     * 更新强化状态
     */
    protected open fun updateBoostStatus() {
        if (isBoosted && boostRemainingTicks > 0) {
            boostRemainingTicks--
            if (boostRemainingTicks <= 0) {
                isBoosted = false
                setChanged()
            }
        }
    }

    /**
     * 更新实际冷却时间
     * 考虑弹药装填倍数、液体强化等因素
     */
    protected open fun updateActualCooldown() {
        var cooldown = baseCooldown.toFloat()

        // 考虑弹药装填倍数
        currentAmmoStats?.let { ammo ->
            cooldown *= ammo.reloadMultiplier
        }

        // 考虑液体强化
        if (isBoosted) {
            config.supportedLiquids.forEach { liquid ->
                if (liquid.affectsFireRate) {
                    cooldown *= (1f - liquid.fireRateMultiplier)
                }
            }
        }

        // 考虑弹药射速倍率
        currentAmmoStats?.let { ammo ->
            cooldown /= ammo.fireRateMultiplier
        }

        actualCooldown = maxOf(1, cooldown.toInt())
    }

    /**
     * 更新旋转角度（平滑旋转）
     */
    protected open fun updateRotation() {
        var deltaYaw = targetYaw - currentYaw

        // 处理角度环绕问题
        deltaYaw = Mth.wrapDegrees(deltaYaw)

        // 限制最大旋转速度
        val maxRotationThisTick = rotationSpeed / 20f
        val previousYaw = currentYaw

        currentYaw += when {
            abs(deltaYaw) > maxRotationThisTick -> sign(deltaYaw) * maxRotationThisTick
            else -> deltaYaw
        }

        // 标记需要同步到客户端
        if (abs(currentYaw - previousYaw) > 0.1f) {
            setChanged()
            syncData()
        }
    }

    /**
     * 更新攻击逻辑
     */
    protected open fun updateAttackLogic(level: Level, pos: BlockPos) {
        if (currentTarget != null && canAttack()) {
            if (attackCooldown <= 0) {
                // 检查弹药
                if (hasAmmo()) {
                    // 发射攻击
                    fireProjectile(level, pos, currentTarget!!)
                    // 消耗弹药
                    consumeAmmo()
                    // 设置冷却时间
                    attackCooldown = actualCooldown
                }
            } else {
                attackCooldown--
            }
        } else {
            // 没有目标或角度不对时重置冷却
            attackCooldown = 0
        }
    }

    /**
     * 检查是否可以攻击（角度是否在容差范围内）
     */
    protected open fun canAttack(): Boolean {
        val deltaYaw = Mth.wrapDegrees(targetYaw - currentYaw)
        return abs(deltaYaw) <= rotationTolerance
    }

    /**
     * 检查是否有弹药
     */
    protected open fun hasAmmo(): Boolean {
        if (!config.hasAmmoSystem) return true
        return currentAmmo > 0 && currentAmmoStats != null
    }

    /**
     * 消耗弹药
     */
    protected open fun consumeAmmo() {
        if (config.hasAmmoSystem && currentAmmo > 0) {
            currentAmmo--
            setChanged()
        }
    }

    // ========== 目标追踪 ==========

    /**
     * 查找最近的敌人
     * @param level 世界
     * @param pos 炮台位置
     * @param range 检测范围
     * @return 最近的目标实体，如果没有则返回 null
     */
    protected open fun findNearestTarget(level: Level, pos: BlockPos, range: Float): LivingEntity? {
        val area = AABB(pos).inflate(range.toDouble())
        val enemies = level.getEntitiesOfClass(
            LivingEntity::class.java,
            area
        ) { e -> isValidTarget(e) }

        return enemies.minByOrNull { it.distanceToSqr(pos.center) }
    }

    /**
     * 检查实体是否为有效目标
     * 根据配置判断是否可以攻击空中/地面单位
     */
    protected open fun isValidTarget(entity: LivingEntity): Boolean {
        if (!entity.isAlive) return false

        // 检查是否为可攻击的实体类型
        val isValidType = entity is Monster || entity is Player
        if (!isValidType) return false

        // 检查目标类型（空中/地面）
        val isFlying = !entity.onGround()
        val canAttack = when {
            isFlying && !config.canAttackAir -> false
            !isFlying && !config.canAttackGround -> false
            else -> true
        }

        return canAttack
    }

    /**
     * 计算到目标的偏航角
     * @param from 起始位置
     * @param to 目标位置
     * @return 偏航角（度）
     */
    protected open fun calculateYawToTarget(from: BlockPos, to: BlockPos): Float {
        val dx = to.x - from.x
        val dz = to.z - from.z
        return (atan2(dz.toDouble(), dx.toDouble()) * (180f / Math.PI)).toFloat() - 90f
    }

    /**
     * 计算射击方向（考虑误差）
     * @param target 目标实体
     * @return 射击方向向量
     */
    protected open fun calculateFireDirection(target: Entity): net.minecraft.world.phys.Vec3 {
        val turretPos = worldPosition.center
        val targetPos = target.position()

        // 基础方向
        var direction = targetPos.subtract(turretPos).normalize()

        // 应用射击误差
        if (config.inaccuracy > 0) {
            val inaccuracyRad = Math.toRadians(config.inaccuracy.toDouble())
            val randomX = (Math.random() - 0.5) * inaccuracyRad
            val randomY = (Math.random() - 0.5) * inaccuracyRad
            val randomZ = (Math.random() - 0.5) * inaccuracyRad

            direction = direction.add(randomX, randomY, randomZ).normalize()
        }

        return direction
    }

    // ========== 攻击行为 ==========

    /**
     * 发射投射物
     * 子类必须实现此方法（仅弹药炮台需要）
     * @param level 世界
     * @param pos 炮台位置
     * @param target 目标实体
     */
    protected open fun fireProjectile(level: Level, pos: BlockPos, target: LivingEntity) {
        // 默认实现：无操作
        // 激光炮台不需要实现此方法
    }

    /**
     * 发射激光
     * 子类可以重写此方法进行自定义激光渲染
     * @param level 世界
     * @param pos 炮台位置
     * @param target 目标实体
     * @param laserStats 激光统计
     */
    protected open fun fireLaser(level: Level, pos: BlockPos, target: LivingEntity, laserStats: LaserStats) {
        // 默认实现：无操作
        // 子类可以重写此方法进行激光渲染
    }

    // ========== 弹药管理 ==========

    /**
     * 添加弹药
     * @return 成功添加的数量
     */
    open fun addAmmo(amount: Int): Int {
        if (!config.hasAmmoSystem) {
            return 0
        }

        val spaceAvailable = config.ammoCapacity - currentAmmo
        val toAdd = minOf(amount, spaceAvailable)

        if (toAdd > 0) {
            currentAmmo += toAdd
            setChanged()
        }

        return toAdd
    }

    /**
     * 设置当前使用的弹药
     */
    open fun setCurrentAmmo(ammoStats: AmmoStats?) {
        if (ammoStats != null && config.supportsAmmo(ammoStats.item)) {
            currentAmmoStats = ammoStats
            updateActualCooldown()
            setChanged()
        }
    }

    // ========== 液体管理 ==========

    /**
     * 添加液体
     * @param amount 添加量
     * @return 成功添加的数量
     */
    open fun addLiquid(amount: Int): Int {
        val fluidCap = fluid ?: return 0
        // 创建一个临时 FluidStack 来填充
        val fluidToFill = if (fluidCap.currentFluid.isEmpty) {
            net.minecraft.world.level.material.Fluids.WATER
        } else {
            fluidCap.currentFluid.fluid
        }
        val fluidStack = net.neoforged.neoforge.fluids.FluidStack(fluidToFill, amount)
        return fluidCap.fill(fluidStack, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)
    }

    /**
     * 消耗液体
     * @param amount 消耗量
     * @return 是否成功消耗
     */
    open fun consumeLiquid(amount: Int): Boolean {
        val fluidCap = fluid ?: return false
        if (fluidCap.currentFluid.amount >= amount) {
            fluidCap.currentFluid.shrink(amount)
            if (fluidCap.currentFluid.isEmpty) {
                fluidCap.currentFluid = net.neoforged.neoforge.fluids.FluidStack.EMPTY
            }
            fluidCap.onFluidChanged()
            return true
        }
        return false
    }

    /**
     * 激活强化
     * @return 是否成功激活
     */
    open fun activateBoost(): Boolean {
        if (!config.hasBoostSupport) {
            return false
        }
        if (isBoosted) {
            return false
        }

        val fluidCap = fluid ?: return false

        // 检查是否有足够的液体
        if (fluidCap.currentFluid.amount >= config.boostLiquidAmount) {
            consumeLiquid(config.boostLiquidAmount)
            isBoosted = true
            boostRemainingTicks = 600 // 30 秒（假设 20 ticks/秒）
            updateActualCooldown()
            setChanged()
            return true
        }

        return false
    }

    // ========== 能量管理 ==========

    /**
     * 添加电力
     * @param amount 添加量（FE）
     * @return 成功添加的数量
     */
    open fun addEnergy(amount: Int): Int {
        val energyCap = energy ?: return 0
        return energyCap.receiveEnergy(amount, false)
    }

    /**
     * 消耗电力
     * @param amount 消耗量（FE）
     * @return 是否成功消耗
     */
    open fun consumeEnergy(amount: Int): Boolean {
        val energyCap = energy ?: return false
        if (energyCap.currentEnergy >= amount) {
            energyCap.extractEnergy(amount, false)
            return true
        }
        return false
    }

    /**
     * 检查是否有足够的电力
     */
    open fun hasEnergy(amount: Int): Boolean {
        val energyCap = energy ?: return false
        return energyCap.currentEnergy >= amount
    }

    // ========== 网络同步 ==========

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        return saveWithoutMetadata(registries)
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener?>? {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    /**
     * 同步数据到客户端
     */
    override fun syncData() {
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }

    // ========== 数据保存 ==========

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putFloat("currentYaw", currentYaw)
        tag.putFloat("targetYaw", targetYaw)
        tag.putInt("attackCooldown", attackCooldown)
        tag.putInt("actualCooldown", actualCooldown)
        tag.putInt("currentAmmo", currentAmmo)
        tag.putBoolean("isBoosted", isBoosted)
        tag.putInt("boostRemainingTicks", boostRemainingTicks)
        tag.putInt("laserDamageTimer", laserDamageTimer)

        // 保存当前弹药信息
        currentAmmoStats?.let { ammo ->
            tag.putString("currentAmmoItem", ammo.item.toString())
        }

        // 能量和液体数据由 ModBlockEntity 自动保存
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        this.currentYaw = tag.getFloat("currentYaw")
        this.targetYaw = tag.getFloat("targetYaw")
        this.attackCooldown = tag.getInt("attackCooldown")
        this.actualCooldown = tag.getInt("actualCooldown")
        this.currentAmmo = tag.getInt("currentAmmo")
        this.isBoosted = tag.getBoolean("isBoosted")
        this.boostRemainingTicks = tag.getInt("boostRemainingTicks")
        this.laserDamageTimer = tag.getInt("laserDamageTimer")

        // 恢复当前弹药信息
        if (tag.contains("currentAmmoItem")) {
            val ammoItemName = tag.getString("currentAmmoItem")
            // TODO: 从注册表获取物品
            // currentAmmoStats = config.getAmmoStats(item)
        }

        // 能量和液体数据由 ModBlockEntity 自动加载
        updateActualCooldown()
    }
}