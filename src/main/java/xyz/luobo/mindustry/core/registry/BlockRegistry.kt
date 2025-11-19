package xyz.luobo.mindustry.core.registry

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.neoforged.neoforge.registries.DeferredRegister
import xyz.luobo.mindustry.Mindustry
import xyz.luobo.mindustry.core.multiblock.MultiblockDummy

object BlockRegistry {
    val MOD_BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(Mindustry.MOD_ID)
    private val controllers = mutableMapOf<ResourceLocation, Block>()
    private val dummies = mutableMapOf<ResourceLocation, Block>()

    fun registerController(machineId: ResourceLocation) {
        val def = MachineRegistry.get(machineId) ?: return
        val block = def.controllerBlock()
        controllers[machineId] = block
        // 注册到 Forge
        MOD_BLOCKS.registerBlock(machineId.path) { block }
    }

    fun registerDummy(machineId: ResourceLocation) {
        val block = MultiblockDummy(BlockBehaviour.Properties.of().noOcclusion())
        dummies[machineId] = block
        MOD_BLOCKS.registerBlock("${machineId.path}_dummy") { block }
    }


    // 在 Mod 初始化时批量注册
    fun initAll() {
        MachineRegistry.machines.keys.forEach { id ->
            registerController(id)
            registerDummy(id)
        }
    }
}