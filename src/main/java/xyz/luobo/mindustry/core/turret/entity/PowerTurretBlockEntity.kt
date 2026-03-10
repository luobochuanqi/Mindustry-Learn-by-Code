package xyz.luobo.mindustry.core.turret.entity

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.common.capabilities.Capability
import net.neoforged.neoforge.common.capabilities.ForgeCapabilities
import net.neoforged.neoforge.common.util.LazyOptional
import net.neoforged.neoforge.energy.EnergyStorage
import net.neoforged.neoforge.energy.IEnergyStorage
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
     * 电力存储
     */
    val energyStorage: EnergyStorage by lazy {
        object : EnergyStorage(
            powerCapacity,
            maxPowerInput,
            maxPowerOutput
        ) {
            override fun onEnergyChanged() {
                this@PowerTurretBlockEntity.onEnergyChanged()
            }
        }
    }

    private val energyStorageOptional: LazyOptional<IEnergyStorage> =
        LazyOptional.of { energyStorage }

    // ========== 状态 ==========

    /**
     * 当前电力
     */
    val currentEnergy: Int
        get() = energyStorage.energyStored

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
        return energyStorage.extractEnergy(amount, true) == amount
    }

    /**
     * 消耗电力
     * @param amount 消耗的电力
     * @return 实际消耗的电力
     */
    fun consumeEnergy(amount: Int): Int {
        val consumed = energyStorage.extractEnergy(amount, false)
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
        val received = energyStorage.receiveEnergy(amount, false)
        if (received > 0) {
            setChanged()
        }
        return received
    }

    /**
     * 电力变化回调
     */
    protected open fun onEnergyChanged() {
        setChanged()
    }

    // ========== 重写父类方法 ==========

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

    // ========== Capability ==========

    override fun <T> getCapability(capability: Capability<T>, side: Direction?): LazyOptional<T> {
        return if (capability == ForgeCapabilities.ENERGY) {
            energyStorageOptional.cast()
        } else {
            super.getCapability(capability, side)
        }
    }

    override fun invalidateCaps() {
        super.invalidateCaps()
        energyStorageOptional.invalidate()
    }

    // ========== 数据保存 ==========

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putInt("energy", energyStorage.energyStored)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        if (tag.contains("energy")) {
            val energy = tag.getInt("energy")
            // 直接设置能量（注意：这里使用了反射或内部方法，实际实现可能需要调整）
            energyStorage.receiveEnergy(energy, false)
        }
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
        return energyStorage.energyStored >= energyStorage.maxEnergyStored
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