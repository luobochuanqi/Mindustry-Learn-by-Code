package xyz.luobo.mindustry.core.registry

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block
import xyz.luobo.mindustry.core.machine.IMachineLogic

object MachineRegistry {
    val machines = mutableMapOf<ResourceLocation, MachineDefinition>()

    fun registerDeferredMachine(id: ResourceLocation, definition: MachineDefinition) {
        machines.put(id, definition)
    }

    fun getDefinitionByBlock(block: Block): MachineDefinition? {
        return machines.values.find {
            it.controllerBlock().javaClass == block.javaClass ||
                    it.block().javaClass == block.javaClass
        }
    }

    fun getDefinitionByPath(path: String): MachineDefinition? {
        return machines.values.find {
            it.controllerBlock().javaClass.name == path ||
                    it.block().javaClass.name == path
        }
    }

    fun register() {
        machines.forEach {
            MultiblockRegistry.registerMultiblock(it.value)
        }
    }
}

data class MachineDefinition(
    val size: Int,
    val controllerBlock: () -> Block,
    val block: () -> Block,
    val energyCapacity: Int = 0,
    val inventorySlots: Int = 0,
    val logic: IMachineLogic
)