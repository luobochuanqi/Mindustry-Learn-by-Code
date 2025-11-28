package xyz.luobo.mindustry.common

import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mindustry.Mindustry
import xyz.luobo.mindustry.common.blocks.PowerNodeBlock
import xyz.luobo.mindustry.common.machines.graphite_press.GraphitePressBlock
import xyz.luobo.mindustry.common.machines.graphite_press.GraphitePressDummyBlock

object ModBlocks {
    val MOD_BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(Mindustry.MOD_ID)

    val POWER_NODE_BLOCK: DeferredBlock<Block> =
        MOD_BLOCKS.registerBlock("power_node_block") { PowerNodeBlock() }

    val GRAPHITE_PRESS_BLOCK: DeferredBlock<Block> =
        MOD_BLOCKS.registerBlock("graphite_press_block") { GraphitePressBlock() }

    val GRAPHITE_DUMMY_BLOCK: DeferredBlock<Block> =
        MOD_BLOCKS.registerBlock("graphite_dummy_block") { GraphitePressDummyBlock() }

    fun register() {
        MOD_BLOCKS.register(MOD_BUS)
    }
}