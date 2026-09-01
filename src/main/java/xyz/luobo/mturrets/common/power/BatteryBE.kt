package xyz.luobo.mturrets.common.power

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.core.capability.impl.EnergyCapabilityImpl
import xyz.luobo.mturrets.core.power.PowerAnchorBE

/**
 * 电池 BE(ADR-0007/#27):储能 80,000 FE,对外充放各限 200 FE/次(防外部一拍抽干致抖振),
 * 图内瞬时。对外充放把差额同步进图聚合缓存(增量记账),图按比例扣账走 [drainFromGrid]。
 */
class BatteryBE(pos: BlockPos, state: BlockState) :
    PowerAnchorBE(ModBlockEntityTypes.BATTERY.get(), pos, state) {

    companion object {
        /** 储能(#27 决议:电池 4000 power × 20 FE)。 */
        const val ENERGY_CAPACITY = 80_000
        /** 对外充放限速(每操作;FE 圈惯例 tick 级速率面)。 */
        const val MAX_TRANSFER = 200
    }

    override val batteryEnergy: Int get() = energyCapability.currentEnergy
    override val batteryCapacity: Int get() = energyCapability.energyCapacity

    /** 对外充放走 capability(图内扣账不走此面,绕开限速)。 */
    override val energyCapability: BatteryEnergyStorage =
        BatteryEnergyStorage(ENERGY_CAPACITY, MAX_TRANSFER, MAX_TRANSFER, { onEnergyChanged() }) { delta ->
            graph?.onBatteryDelta(delta)
        }

    /** 图按比例扣账:直接扣本块电量并同步聚合(确定性面,与对外 capability 同一余额)。 */
    override fun drainFromGrid(amount: Int) {
        if (amount <= 0) return
        energyCapability.currentEnergy -= amount
        graph?.onBatteryDelta(-amount)
        energyCapability.onEnergyChanged()
    }

    /**
     * 图按比例充电:直改本块电量并同步聚合(确定性面,与 [drainFromGrid] 对称、图内
     * 瞬时),不走对外 capability 的限速面——图内充放都是瞬时记账,限速只约束对外交互。
     */
    override fun chargeFromGrid(amount: Int) {
        if (amount <= 0) return
        val before = energyCapability.currentEnergy
        energyCapability.currentEnergy = (before + amount).coerceAtMost(energyCapability.energyCapacity)
        val added = energyCapability.currentEnergy - before
        if (added > 0) {
            graph?.onBatteryDelta(added)
            energyCapability.onEnergyChanged()
        }
    }
}

/** 电池储能:对外充放后把差额同步进图聚合缓存(simulate 不记账,防重复扣减)。 */
class BatteryEnergyStorage(
    capacity: Int,
    maxReceive: Int,
    maxExtract: Int,
    onChanged: () -> Unit,
    private val onMutated: (Int) -> Unit
) : EnergyCapabilityImpl(capacity, maxReceive, maxExtract, onChanged) {

    override fun receiveEnergy(toReceive: Int, simulate: Boolean): Int {
        val accepted = super.receiveEnergy(toReceive, simulate)
        if (!simulate && accepted > 0) onMutated(accepted)
        return accepted
    }

    override fun extractEnergy(toExtract: Int, simulate: Boolean): Int {
        val extracted = super.extractEnergy(toExtract, simulate)
        if (!simulate && extracted > 0) onMutated(-extracted)
        return extracted
    }
}