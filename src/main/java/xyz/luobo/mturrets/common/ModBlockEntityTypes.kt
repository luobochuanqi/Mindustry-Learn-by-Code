package xyz.luobo.mturrets.common

import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mturrets.MTurrets
import xyz.luobo.mturrets.common.power.BatteryBE
import xyz.luobo.mturrets.common.power.PowerNodeBE
import xyz.luobo.mturrets.common.structure.TestStructureAnchorBE
import xyz.luobo.mturrets.common.machines.drill.DrillBE
import xyz.luobo.mturrets.common.machines.kiln.KilnBE
import xyz.luobo.mturrets.common.turrets.ArcTurretBlockEntity
import xyz.luobo.mturrets.common.turrets.DuoTurretBE
import xyz.luobo.mturrets.common.turrets.MeltdownTurretBlockEntity
import java.util.function.Supplier

object ModBlockEntityTypes {
    val BLOCK_ENTITY_TYPES: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MTurrets.MOD_ID)

    // 电网(ADR-0007,#30)
    val POWER_NODE: DeferredHolder<BlockEntityType<*>, BlockEntityType<PowerNodeBE>> =
        BLOCK_ENTITY_TYPES.register("power_node", Supplier {
            BlockEntityType.Builder.of(
                { pos, state -> PowerNodeBE(pos, state) },
                ModBlocks.POWER_NODE.get()
            ).build(null) // dataType 为 null, 使用 NBT, 拒绝使用 Minecraft 1.20.5+ 引入的 数据组件(Data Components) 特性
        })
    val BATTERY: DeferredHolder<BlockEntityType<*>, BlockEntityType<BatteryBE>> =
        BLOCK_ENTITY_TYPES.register("battery", Supplier {
            BlockEntityType.Builder.of(
                { pos, state -> BatteryBE(pos, state) },
                ModBlocks.BATTERY.get()
            ).build(null)
        })

    // Machines
    val KILN: DeferredHolder<BlockEntityType<*>, BlockEntityType<KilnBE>> =
        BLOCK_ENTITY_TYPES.register("kiln", Supplier {
            BlockEntityType.Builder.of(
                { pos, state -> KilnBE(pos, state) },
                ModBlocks.KILN.get()
            ).build(null)
        })
    // 钻头(#35)
    val DRILL: DeferredHolder<BlockEntityType<*>, BlockEntityType<DrillBE>> =
        BLOCK_ENTITY_TYPES.register("mechanical_drill", Supplier {
            BlockEntityType.Builder.of(
                { pos, state -> DrillBE(pos, state) },
                ModBlocks.DRILL.get()
            ).build(null)
        })

    // Turrets - 新的 MTurrets 风格炮台系统
    val DUO_BLOCK_ENTITY: DeferredHolder<BlockEntityType<*>, BlockEntityType<DuoTurretBE>> =
        BLOCK_ENTITY_TYPES.register("duo", Supplier {
            BlockEntityType.Builder.of(
                { pos, state -> DuoTurretBE(pos, state) },
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

    // 蓝图管线(ADR-0003/0004):骨架临时测试锚点 BE,2×2 测试锚点专用(#34 落地后删除)
    val TEST_STRUCTURE_ANCHOR_BE: DeferredHolder<BlockEntityType<*>, BlockEntityType<TestStructureAnchorBE>> =
        BLOCK_ENTITY_TYPES.register("test_structure_anchor", Supplier {
            BlockEntityType.Builder.of(
                { pos, state -> TestStructureAnchorBE(pos, state) },
                ModBlocks.TEST_STRUCTURE_ANCHOR_2X2.get()
            ).build(null)
        })


    fun register() {
        BLOCK_ENTITY_TYPES.register(MOD_BUS)
    }
}