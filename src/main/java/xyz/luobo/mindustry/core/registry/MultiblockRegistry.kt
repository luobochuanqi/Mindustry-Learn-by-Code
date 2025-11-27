package xyz.luobo.mindustry.core.registry

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.registries.DeferredRegister
import xyz.luobo.mindustry.Mindustry

object MultiblockRegistry {
    val MOD_BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(Mindustry.MOD_ID)
    private val controllers = mutableMapOf<ResourceLocation, (() -> Block)?>()
    private val dummies = mutableMapOf<ResourceLocation, (() -> Block)?>()

    fun registerController(machineId: ResourceLocation) {
        val def = MachineRegistry.get(machineId) ?: return
        controllers[machineId] = def.controllerBlock
        // Register with the deferred registry - defer the actual block creation
        MOD_BLOCKS.registerBlock(machineId.path) { def.controllerBlock() }
    }

    fun registerDummy(machineId: ResourceLocation) {
        dummies[machineId] = {
            MachineRegistry.get(machineId)?.let {
                xyz.luobo.mindustry.core.multiblock.MultiblockDummy(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().noOcclusion()
                )
            } ?: throw IllegalStateException("Machine definition not found for $machineId")
        }

        MOD_BLOCKS.registerBlock("${machineId.path}_dummy") {
            xyz.luobo.mindustry.core.multiblock.MultiblockDummy(
                net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().noOcclusion()
            )
        }
    }

    fun getController(id: ResourceLocation): (() -> Block)? = controllers[id]
    fun getDummy(id: ResourceLocation): (() -> Block)? = dummies[id]
}