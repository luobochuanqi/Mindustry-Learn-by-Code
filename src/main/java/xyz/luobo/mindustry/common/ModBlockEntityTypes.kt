package xyz.luobo.mindustry.common

import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mindustry.Mindustry
import xyz.luobo.mindustry.common.blockEntities.PowerNodeBlockEntity
import xyz.luobo.mindustry.common.machines.kiln.KilnBE
import xyz.luobo.mindustry.common.turrets.duo.DuoBE
import java.util.function.Supplier

object ModBlockEntityTypes {
    val BLOCK_ENTITY_TYPES: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Mindustry.MOD_ID)

    val POWER_NODE_BLOCK_ENTITY: DeferredHolder<BlockEntityType<*>, BlockEntityType<PowerNodeBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("power_node", Supplier {
            BlockEntityType.Builder.of(
                { pos, state -> PowerNodeBlockEntity(pos, state) },
                ModBlocks.POWER_NODE_BLOCK.get()
            ).build(null) // dataType 为 null, 使用 NBT, 拒绝使用 Minecraft 1.20.5+ 引入的 数据组件(Data Components) 特性
        })

    // Machines
    val KILN_BLOCK_ENTITY: DeferredHolder<BlockEntityType<*>, BlockEntityType<KilnBE>> =
        BLOCK_ENTITY_TYPES.register("kiln", Supplier {
            BlockEntityType.Builder.of(
                { pos, state -> KilnBE(pos, state) },
                ModBlocks.KILN_BLOCK.get()
            ).build(null)
        })

    // Turrets
    val DUO_Block_Entity: DeferredHolder<BlockEntityType<*>, BlockEntityType<DuoBE>> =
        BLOCK_ENTITY_TYPES.register("duo", Supplier {
            BlockEntityType.Builder.of(
                { pos, state -> DuoBE(pos, state) },
                ModBlocks.DUO_BLOCK.get()
            ).build(null)
        })

    fun registerBy() {
    }

    fun register() {
        BLOCK_ENTITY_TYPES.register(MOD_BUS)
    }
}