package xyz.luobo.mturrets.common

import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mturrets.MTurrets
import xyz.luobo.mturrets.common.blockEntities.PowerNodeBlockEntity
import xyz.luobo.mturrets.common.structure.TestStructureAnchorBE
import xyz.luobo.mturrets.common.machines.kiln.KilnBE
import xyz.luobo.mturrets.common.turrets.ArcTurretBlockEntity
import xyz.luobo.mturrets.common.turrets.DuoTurretBlockEntity
import xyz.luobo.mturrets.common.turrets.MeltdownTurretBlockEntity
import java.util.function.Supplier

object ModBlockEntityTypes {
    val BLOCK_ENTITY_TYPES: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MTurrets.MOD_ID)

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

    // Turrets - 新的 MTurrets 风格炮台系统
    val DUO_BLOCK_ENTITY: DeferredHolder<BlockEntityType<*>, BlockEntityType<DuoTurretBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("duo", Supplier {
            BlockEntityType.Builder.of(
                { pos, state -> DuoTurretBlockEntity(pos, state) },
                ModBlocks.DUO_BLOCK.get()
            ).build(null)
        })

    val ARC_BLOCK_ENTITY: DeferredHolder<BlockEntityType<*>, BlockEntityType<ArcTurretBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("arc", Supplier {
            BlockEntityType.Builder.of(
                { pos, state -> ArcTurretBlockEntity(pos, state) },
                ModBlocks.ARC_BLOCK.get()
            ).build(null)
        })

    val MELTDOWN_BLOCK_ENTITY: DeferredHolder<BlockEntityType<*>, BlockEntityType<MeltdownTurretBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("meltdown", Supplier {
            BlockEntityType.Builder.of(
                { pos, state -> MeltdownTurretBlockEntity(pos, state) },
                ModBlocks.MELTDOWN_BLOCK.get()
            ).build(null)
        })

    // 蓝图管线(ADR-0003/0004):骨架临时测试锚点 BE,两块测试锚点共用
    val TEST_STRUCTURE_ANCHOR_BE: DeferredHolder<BlockEntityType<*>, BlockEntityType<TestStructureAnchorBE>> =
        BLOCK_ENTITY_TYPES.register("test_structure_anchor", Supplier {
            BlockEntityType.Builder.of(
                { pos, state -> TestStructureAnchorBE(pos, state) },
                ModBlocks.TEST_STRUCTURE_ANCHOR_2X2.get(),
                ModBlocks.TEST_STRUCTURE_ANCHOR_1X1.get()
            ).build(null)
        })


    fun register() {
        BLOCK_ENTITY_TYPES.register(MOD_BUS)
    }
}