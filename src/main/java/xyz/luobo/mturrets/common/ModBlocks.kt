package xyz.luobo.mturrets.common

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mturrets.MTurrets
import xyz.luobo.mturrets.common.power.BatteryBlock
import xyz.luobo.mturrets.common.power.PowerNodeBlock
import xyz.luobo.mturrets.common.structure.TestStructureAnchor2x2Block
import xyz.luobo.mturrets.core.structure.BlueprintAnchorBlock
import xyz.luobo.mturrets.core.structure.StructuralBlock
import xyz.luobo.mturrets.common.machines.kiln.KilnBlock
import xyz.luobo.mturrets.common.machines.drill.DrillBlock
import xyz.luobo.mturrets.common.turrets.DuoBlock
import xyz.luobo.mturrets.common.turrets.ScatterBlock

object ModBlocks {
    val MOD_BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(MTurrets.MOD_ID)

    // 电网(ADR-0007,#30):节点纯导线零储能;电池储能 8 万 FE。均 1×1 蓝图锚点。
    val POWER_NODE: DeferredBlock<Block> = MOD_BLOCKS.registerBlock("power_node") { PowerNodeBlock() }
    val BATTERY: DeferredBlock<Block> = MOD_BLOCKS.registerBlock("battery") { BatteryBlock() }


    // Machines
    val KILN: DeferredBlock<Block> = MOD_BLOCKS.registerBlock("kiln") { KilnBlock() }

    // 矿脉与钻头(#35,ADR-0008 一期材料链):单变体矿石(无深色变体,#24 视觉妥协);
    // 钻头 = 2×2 蓝图锚点,角锚点 +X/+Z 生长(#26),成员格用钻头外观结构块。
    val ORE_COPPER: DeferredBlock<Block> = MOD_BLOCKS.registerBlock("ore_copper") { Block(oreProperties()) }
    val ORE_LEAD: DeferredBlock<Block> = MOD_BLOCKS.registerBlock("ore_lead") { Block(oreProperties()) }
    val ORE_COAL: DeferredBlock<Block> = MOD_BLOCKS.registerBlock("ore_coal") { Block(oreProperties()) }
    val DRILL: DeferredBlock<DrillBlock> = MOD_BLOCKS.registerBlock("mechanical_drill") { DrillBlock() }
    val DRILL_STRUCTURAL: DeferredBlock<StructuralBlock> =
        MOD_BLOCKS.registerBlock("mechanical_drill_structural") {
            StructuralBlock(BlueprintAnchorBlock.structureProperties().noLootTable())
        }


    /** 矿石属性:硬度同原版矿石;无 requiresCorrectToolForDrops(#24 手挖亦可)。 */
    private fun oreProperties(): BlockBehaviour.Properties =
        BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0f)

    // Turrets - 新的 MTurrets 风格炮台系统
    val DUO_BLOCK: DeferredBlock<Block> = MOD_BLOCKS.registerBlock("duo") { DuoBlock() }
    // Scatter(#34):2×2 蓝图锚点 + 基座成员格(无物品,掉落收口在锚点)
    val SCATTER: DeferredBlock<Block> = MOD_BLOCKS.registerBlock("scatter") { ScatterBlock() }
    val SCATTER_STRUCTURAL: DeferredBlock<StructuralBlock> =
        MOD_BLOCKS.registerBlock("scatter_structural") {
            StructuralBlock(BlueprintAnchorBlock.structureProperties().noLootTable())
        }

    // 蓝图管线(ADR-0003):骨架临时测试方块,真内容(#33/#34)复用同基类后删除
    val TEST_STRUCTURAL: DeferredBlock<StructuralBlock> =
        MOD_BLOCKS.registerBlock("test_structure_structural") {
            StructuralBlock(BlueprintAnchorBlock.structureProperties().noLootTable())
        }
    val TEST_STRUCTURE_ANCHOR_2X2: DeferredBlock<TestStructureAnchor2x2Block> =
        MOD_BLOCKS.registerBlock("test_structure_anchor_2x2") { TestStructureAnchor2x2Block() }


    fun register() {
        MOD_BLOCKS.register(MOD_BUS)
    }
}