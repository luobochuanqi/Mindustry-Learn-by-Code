package xyz.luobo.mindustry.core.energy

import net.neoforged.neoforge.energy.EnergyStorage
import xyz.luobo.mindustry.core.machine.BaseMachineBE

open class MachineEnergyStorage(
    capability: Int,
    val be: BaseMachineBE
) : EnergyStorage(capability, capability, be.energyPerTick, 0) {
    open fun onEnergyChanged() {
        be.setChanged()
        be.syncData()
    }
}