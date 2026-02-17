package xyz.luobo.mindustry.core.capability

import net.neoforged.neoforge.energy.IEnergyStorage

/**
 * 能量 Capability 接口
 * 定义能量存储的基本功能
 */
interface IEnergyCapability : IEnergyStorage {

    /**
     * 能量容量
     */
    val energyCapacity: Int

    /**
     * 最大输入速率（每 tick）
     */
    val maxReceive: Int

    /**
     * 最大输出速率（每 tick）
     */
    val maxExtract: Int

    /**
     * 当前存储的能量
     */
    var currentEnergy: Int

    /**
     * 能量变化回调
     */
    fun onEnergyChanged()

    /**
     * 检查是否可以接收能量
     */
    override fun canReceive(): Boolean = maxReceive > 0

    /**
     * 检查是否可以输出能量
     */
    override fun canExtract(): Boolean = maxExtract > 0

    /**
     * 接收能量
     */
    override fun receiveEnergy(toReceive: Int, simulate: Boolean): Int {
        if (!canReceive() || toReceive <= 0) return 0

        val energyReceived = kotlin.math.min(
            maxReceive,
            kotlin.math.min(toReceive, energyCapacity - currentEnergy)
        )

        if (!simulate && energyReceived > 0) {
            currentEnergy += energyReceived
            onEnergyChanged()
        }

        return energyReceived
    }

    /**
     * 提取能量
     */
    override fun extractEnergy(toExtract: Int, simulate: Boolean): Int {
        if (!canExtract() || toExtract <= 0) return 0

        val energyExtracted = kotlin.math.min(
            maxExtract,
            kotlin.math.min(toExtract, currentEnergy)
        )

        if (!simulate && energyExtracted > 0) {
            currentEnergy -= energyExtracted
            onEnergyChanged()
        }

        return energyExtracted
    }

    /**
     * 获取当前存储的能量
     */
    override fun getEnergyStored(): Int = currentEnergy

    /**
     * 获取最大能量容量
     */
    override fun getMaxEnergyStored(): Int = energyCapacity

    /**
     * 获取能量存储百分比
     */
    fun getEnergyPercentage(): Float {
        if (energyCapacity <= 0) return 0f
        return currentEnergy.toFloat() / energyCapacity.toFloat()
    }

    /**
     * 检查是否有足够的能量
     */
    fun hasEnergy(amount: Int): Boolean = currentEnergy >= amount

    /**
     * 设置能量（直接设置，用于数据加载）
     */
    fun setEnergy(amount: Int) {
        currentEnergy = amount.coerceIn(0, energyCapacity)
        onEnergyChanged()
    }
}