package xyz.luobo.mindustry.core.registry

import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mindustry.Mindustry

object MultiblockRegistry {
    val MULTIBLOCK_ENTITY_TYPES: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Mindustry.MOD_ID)

    val MULTIBLOCK_BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(Mindustry.MOD_ID)

    val MULTIBLOCK_BLOCKS_ITEMS: DeferredRegister.Items = DeferredRegister.createItems(Mindustry.MOD_ID)


    fun register() {
        // TODO("实现统一注册各个 Multiblock 的 Block(用于实现互动逻辑), DummyBlock(用于重定向操作到 Block), BlockEntity(ControllerBlock(用于执行逻辑), DummyBlock(用于保存数据))")
        MULTIBLOCK_BLOCKS.register(MOD_BUS)
        MULTIBLOCK_BLOCKS_ITEMS.register(MOD_BUS)
        MULTIBLOCK_ENTITY_TYPES.register(MOD_BUS)
    }

    fun registerMultiblock(definition: MachineDefinition) {
        // TODO("I want to rest. T T")
    }
}