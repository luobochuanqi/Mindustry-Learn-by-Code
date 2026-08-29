package xyz.luobo.mturrets.common

import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mturrets.MTurrets
import xyz.luobo.mturrets.common.structure.TestStructureAnchor1x1Block
import xyz.luobo.mturrets.common.structure.TestStructureAnchor2x2Block
import xyz.luobo.mturrets.core.structure.BlueprintAnchorBlock
import xyz.luobo.mturrets.core.structure.StructuralBlock
import xyz.luobo.mturrets.common.blocks.PowerNodeBlock
import xyz.luobo.mturrets.common.machines.kiln.KilnBlock
import xyz.luobo.mturrets.common.turrets.ArcTurretBlock
import xyz.luobo.mturrets.common.turrets.DuoTurretBlock
import xyz.luobo.mturrets.common.turrets.MeltdownTurretBlock

object ModBlocks {
    val MOD_BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(MTurrets.MOD_ID)

    val POWER_NODE_BLOCK: DeferredBlock<Block> = MOD_BLOCKS.registerBlock("power_node_block") { PowerNodeBlock() }

    // Machines
    val KILN_BLOCK: DeferredBlock<Block> = MOD_BLOCKS.registerBlock("kiln_block") { KilnBlock() }

    // Turrets - 新的 MTurrets 风格炮台系统
    val DUO_BLOCK: DeferredBlock<Block> = MOD_BLOCKS.registerBlock("duo") { DuoTurretBlock() }
    val ARC_BLOCK: DeferredBlock<Block> = MOD_BLOCKS.registerBlock("arc") { ArcTurretBlock() }
    val MELTDOWN_BLOCK: DeferredBlock<Block> = MOD_BLOCKS.registerBlock("meltdown") { MeltdownTurretBlock() }

    // 蓝图管线(ADR-0003):骨架临时测试方块,真内容(#33/#34)复用同基类后删除
    val TEST_STRUCTURAL: DeferredBlock<StructuralBlock> =
        MOD_BLOCKS.registerBlock("test_structure_structural") {
            StructuralBlock(BlueprintAnchorBlock.structureProperties().noLootTable())
        }
    val TEST_STRUCTURE_ANCHOR_2X2: DeferredBlock<TestStructureAnchor2x2Block> =
        MOD_BLOCKS.registerBlock("test_structure_anchor_2x2") { TestStructureAnchor2x2Block() }
    val TEST_STRUCTURE_ANCHOR_1X1: DeferredBlock<TestStructureAnchor1x1Block> =
        MOD_BLOCKS.registerBlock("test_structure_anchor_1x1") { TestStructureAnchor1x1Block() }

    fun register() {
        MOD_BLOCKS.register(MOD_BUS)
    }
}