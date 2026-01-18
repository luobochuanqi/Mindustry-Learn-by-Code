package xyz.luobo.mindustry.core.energy

import net.neoforged.neoforge.energy.EnergyStorage

open class MachineEnergyStorage(capability: Int) : EnergyStorage(capability, capability, 0, 0) {
    open fun onEnergyChanged() {

    }
}