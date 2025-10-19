package xyz.luobo.mindustry.Common

import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mindustry.Common.Blocks.PowerNodeBlock
import xyz.luobo.mindustry.Mindustry

object ModBlocks {
    val MOD_BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(Mindustry.MOD_ID)

    val POWER_NODE_BLOCK: DeferredBlock<Block> =
        MOD_BLOCKS.registerBlock("power_node_block") { PowerNodeBlock() }

    fun register() {
        MOD_BLOCKS.register(MOD_BUS)

    }
}