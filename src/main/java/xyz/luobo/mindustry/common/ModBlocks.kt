package xyz.luobo.mindustry.common

import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mindustry.Mindustry
import xyz.luobo.mindustry.common.blocks.GraphitePressControllerBlock
import xyz.luobo.mindustry.common.blocks.MultiBlockDummyBlock
import xyz.luobo.mindustry.common.blocks.PowerNodeBlock

object ModBlocks {
    val MOD_BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(Mindustry.MOD_ID)

    val POWER_NODE_BLOCK: DeferredBlock<Block> =
        MOD_BLOCKS.registerBlock("power_node_block") { PowerNodeBlock() }

    val GRAPHITE_PRESS_BLOCK: DeferredBlock<Block> =
        MOD_BLOCKS.registerBlock("graphite_press") { GraphitePressControllerBlock() }

    val MULTI_BLOCK_DUMMY_BLOCK: DeferredBlock<Block> =
        MOD_BLOCKS.registerBlock("multi_block_dummy_block") { MultiBlockDummyBlock() }

    fun register() {
        MOD_BLOCKS.register(MOD_BUS)
    }
}