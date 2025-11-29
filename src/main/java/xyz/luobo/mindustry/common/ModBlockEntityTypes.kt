package xyz.luobo.mindustry.common

import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mindustry.Mindustry
import xyz.luobo.mindustry.common.blockEntities.PowerNodeBlockEntity
import xyz.luobo.mindustry.common.machines.graphite_press.GraphitePressBlockEntity
import xyz.luobo.mindustry.common.machines.graphite_press.GraphitePressDummyBlockEntity
import java.util.function.Supplier

object ModBlockEntityTypes {
    val BLOCK_ENTITY_TYPES: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Mindustry.MOD_ID)

    val POWER_NODE_BLOCK_ENTITY: DeferredHolder<BlockEntityType<*>, BlockEntityType<PowerNodeBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("power_node", Supplier{
            BlockEntityType.Builder.of(
                { pos, state -> PowerNodeBlockEntity(pos, state) },
                ModBlocks.POWER_NODE_BLOCK.get()
            ).build(null) // dataType 为 null, 使用 NBT, 拒绝使用 Minecraft 1.20.5+ 引入的 数据组件(Data Components) 特性
        })

    val GRAPHITE_PRESS_BLOCK_ENTITY: DeferredHolder<BlockEntityType<*>, BlockEntityType<GraphitePressBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("graphite_press", Supplier {
            BlockEntityType.Builder.of(
                { pos, state -> GraphitePressBlockEntity(pos, state) },
                ModBlocks.GRAPHITE_PRESS_BLOCK.get()
            ).build(null)
        })

    val GRAPHITE_PRESS_PART_BLOCK_ENTITY: DeferredHolder<BlockEntityType<*>, BlockEntityType<GraphitePressDummyBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("graphite_press_part", Supplier {
            BlockEntityType.Builder.of(
                { pos, state -> GraphitePressDummyBlockEntity(pos, state) },
                ModBlocks.GRAPHITE_DUMMY_BLOCK.get()
            ).build(null)
        })

    fun registerBy() {
    }

    fun register() {
        BLOCK_ENTITY_TYPES.register(MOD_BUS)
    }
}