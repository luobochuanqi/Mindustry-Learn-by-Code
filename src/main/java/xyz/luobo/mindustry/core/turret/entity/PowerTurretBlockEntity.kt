package xyz.luobo.mindustry.core.turret.entity

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mindustry.core.capability.IEnergyCapability
import xyz.luobo.mindustry.core.capability.impl.EnergyCapabilityImpl
import xyz.luobo.mindustry.core.turret.bullet.BulletType

/**
 * 电力炮台实体
 * 使用电力作为能源，具有单一的 shootType
 * 模仿 Mindustry 的 PowerTurret
 *
 * @param type BlockEntityType
 * @param pos 方块位置
 * @param state 方块状态
 */
abstract class PowerTurretBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : ReloadTurretBlockEntity(type, pos, state) {

    // ========== 电力配置（子类必须实现）==========

    /**
     * 射击类型
     * 电力炮台只有一种射击类型
     */
    abstract val shootType: BulletType

    /**
     * 每次射击消耗的电力（FE）
     */
    abstract val powerPerShot: Int

    /**
     * 电力容量
     * 子类可以覆盖
     */
    open val powerCapacity: Int = 10000

    /**
     * 最大电力输入速率
     */
    open val maxPowerInput: Int = 1000

    /**
     * 最大电力输出速率（炮台通常不输出）
     */
    open val maxPowerOutput: Int = 0

    // ========== 电力 Capability ==========

    /**
     * 能量 Capability 实现
     */
    override val energyCapability: EnergyCapabilityImpl by lazy {
        createEnergyCapability(
            capacity = powerCapacity,
            maxReceive = maxPowerInput,
            maxExtract = maxPowerOutput
        )
    }

    /**
     * 能量处理便捷访问
     */
    protected val energyHandler: IEnergyCapability
        get() = energyCapability

    // ========== 状态 ==========

    /**
     * 当前电力
     */
    val currentEnergy: Int
        get() = energyHandler.currentEnergy

    /**
     * 电力填充百分比
     */
    val energyFillPercent: Float
        get() = currentEnergy.toFloat() / powerCapacity

    // ========== 电力管理 ==========

    /**
     * 检查是否有足够电力
     * @param amount 需要的电力
     * @return 是否有足够电力
     */
    fun hasEnergy(amount: Int): Boolean {
        return energyHandler.extractEnergy(amount, true) == amount
    }

    /**
     * 消耗电力
     * @param amount 消耗的电力
     * @return 实际消耗的电力
     */
    fun consumeEnergy(amount: Int): Int {
        val consumed = energyHandler.extractEnergy(amount, false)
        if (consumed > 0) {
            setChanged()
        }
        return consumed
    }

    /**
     * 添加电力
     * @param amount 添加的电力
     * @return 实际添加的电力
     */
    fun receiveEnergy(amount: Int): Int {
        val received = energyHandler.receiveEnergy(amount, false)
        if (received > 0) {
            setChanged()
        }
        return received
    }

    // ========== 重写父类方法 ==========

    /**
     * 检查是否可以射击
     */
    override fun canShoot(): Boolean {
        return super.canShoot() && hasEnergy(powerPerShot)
    }

    override fun consumeAmmo() {
        // 电力炮台消耗电力而非物品
        consumeEnergy(powerPerShot)
    }

    override fun hasAmmo(): Boolean {
        return currentEnergy >= powerPerShot
    }

    /**
     * 获取当前射击类型
     */
    open fun getCurrentBulletType(): BulletType {
        return shootType
    }

    // ========== 数据保存 ==========

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        // 能量数据由父类 MindustryModBlockEntity 自动保存
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        // 能量数据由父类 MindustryModBlockEntity 自动加载
    }

    // ========== 便捷方法 ==========

    /**
     * 检查电力是否充足（超过50%）
     */
    fun isPowerSufficient(): Boolean {
        return energyFillPercent >= 0.5f
    }

    /**
     * 检查电力是否已满
     */
    fun isPowerFull(): Boolean {
        return energyHandler.currentEnergy >= energyHandler.energyCapacity
    }

    /**
     * 获取所需电力
     */
    fun getPowerNeeded(): Int {
        return powerPerShot - currentEnergy
    }

    /**
     * 获取每次射击的电力消耗
     */
    fun getPowerConsumptionPerShot(): Int {
        return powerPerShot
    }

    /**
     * 计算在满电状态下可以射击多少次
     */
    fun calculateMaxShots(): Int {
        return if (powerPerShot > 0) {
            currentEnergy / powerPerShot
        } else {
            Int.MAX_VALUE
        }
    }
}
