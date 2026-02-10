package xyz.luobo.mindustry.common

import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mindustry.Mindustry
import xyz.luobo.mindustry.common.blocks.PowerNodeBlock
import xyz.luobo.mindustry.common.machines.kiln.KilnBlock
import xyz.luobo.mindustry.common.turrets.duo.DuoBlock

object ModBlocks {
    val MOD_BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(Mindustry.MOD_ID)

    val POWER_NODE_BLOCK: DeferredBlock<Block> = MOD_BLOCKS.registerBlock("power_node_block") { PowerNodeBlock() }

    // Machines
    val KILN_BLOCK: DeferredBlock<Block> = MOD_BLOCKS.registerBlock("kiln_block") { KilnBlock() }

    // Turrets
    val DUO_BLOCK: DeferredBlock<Block> = MOD_BLOCKS.registerBlock("duo") { DuoBlock() }

    fun register() {
        MOD_BLOCKS.register(MOD_BUS)
    }
}