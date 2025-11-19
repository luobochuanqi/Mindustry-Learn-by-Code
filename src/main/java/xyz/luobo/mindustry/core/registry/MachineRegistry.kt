package xyz.luobo.mindustry.core.registry

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mindustry.core.machine.IMachineLogic

object MachineRegistry {
    val machines = mutableMapOf<ResourceLocation, MachineDefinition>()

    fun register(id: ResourceLocation, definition: MachineDefinition) {
        machines[id] = definition
    }

    fun get(id: ResourceLocation): MachineDefinition? = machines[id]
}

data class MachineDefinition(
    val size: Int,                  // 2 = 2x2, 3 = 3x3
    val controllerBlock: () -> Block,
    val blockEntity: (BlockPos, BlockState) -> BlockEntity,
    val texture: ResourceLocation,  // 用于渲染
    val energyCapacity: Int = 0,
    val inventorySlots: Int = 0,
    val logic: IMachineLogic       // 机器行为
)