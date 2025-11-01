package xyz.luobo.mindustry.common

import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mindustry.Mindustry
import xyz.luobo.mindustry.common.blockEntities.GraphitePressController
import xyz.luobo.mindustry.common.blockEntities.PowerNodeBlockEntity
import java.util.function.Supplier

object  ModBlockEntities {
    val BLOCK_ENTITY_TYPES: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Mindustry.MOD_ID)

    val POWER_NODE_BLOCK_ENTITY: DeferredHolder<BlockEntityType<*>, BlockEntityType<PowerNodeBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("power_node", Supplier{
            BlockEntityType.Builder.of(
                { pos, state -> PowerNodeBlockEntity(pos, state) },
                ModBlocks.POWER_NODE_BLOCK.get()
            ).build(null) // dataType 为 null, 使用 NBT, 拒绝使用 Minecraft 1.20.5+ 引入的 数据组件(Data Components) 特性
        })

    val GRAPHITE_PRESS_CONTROLLER: DeferredHolder<BlockEntityType<*>, BlockEntityType<GraphitePressController>> =
        BLOCK_ENTITY_TYPES.register("graphite_press_controller", Supplier{
            BlockEntityType.Builder.of(
                { pos, state -> GraphitePressController(pos, state) },
                ModBlocks.GRAPHITE_PRESS_BLOCK.get()
            ).build(null)
        })

    fun register() {
        BLOCK_ENTITY_TYPES.register(MOD_BUS)
    }
}