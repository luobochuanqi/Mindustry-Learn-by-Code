package xyz.luobo.mindustry.core.registry

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block
import xyz.luobo.mindustry.core.machine.IMachineLogic

object MachineRegistry {
    val machines = mutableMapOf<ResourceLocation, MachineDefinition>()

    fun register(id: ResourceLocation, definition: MachineDefinition) {
        machines[id] = definition
    }

    fun getDefinition(id: ResourceLocation): MachineDefinition? = machines[id]
}

data class MachineDefinition(
    val size: Int,
    val controllerBlock: () -> Block,
    val block: () -> Block,
    val energyCapacity: Int = 0,
    val inventorySlots: Int = 0,
    val logic: IMachineLogic
)