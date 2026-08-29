package xyz.luobo.mturrets.core.capability.impl

import xyz.luobo.mturrets.core.capability.IEnergyCapability

/**
 * 能量 Capability 默认实现
 */
open class EnergyCapabilityImpl(
    override val energyCapacity: Int,
    override val maxReceive: Int = 0,
    override val maxExtract: Int = 0,
    private val onEnergyChangedCallback: () -> Unit = {}
) : IEnergyCapability {

    override var currentEnergy: Int = 0
        set(value) {
            field = value.coerceIn(0, energyCapacity)
        }

    override fun onEnergyChanged() {
        onEnergyChangedCallback()
    }

    /**
     * 复制当前状态
     */
    fun copy(): EnergyCapabilityImpl {
        return EnergyCapabilityImpl(energyCapacity, maxReceive, maxExtract, onEnergyChangedCallback).apply {
            currentEnergy = this@EnergyCapabilityImpl.currentEnergy
        }
    }
}