package xyz.luobo.mindustry.common

import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mindustry.Mindustry
import xyz.luobo.mindustry.common.blocks.PowerNodeBlock
import xyz.luobo.mindustry.common.machines.kiln.KilnBlock
import xyz.luobo.mindustry.common.turrets.ArcTurretBlock
import xyz.luobo.mindustry.common.turrets.DuoTurretBlock
import xyz.luobo.mindustry.common.turrets.MeltdownTurretBlock

object ModBlocks {
    val MOD_BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(Mindustry.MOD_ID)

    val POWER_NODE_BLOCK: DeferredBlock<Block> = MOD_BLOCKS.registerBlock("power_node_block") { PowerNodeBlock() }

    // Machines
    val KILN_BLOCK: DeferredBlock<Block> = MOD_BLOCKS.registerBlock("kiln_block") { KilnBlock() }

    // Turrets - 新的 Mindustry 风格炮台系统
    val DUO_BLOCK: DeferredBlock<Block> = MOD_BLOCKS.registerBlock("duo") { DuoTurretBlock() }
    val ARC_BLOCK: DeferredBlock<Block> = MOD_BLOCKS.registerBlock("arc") { ArcTurretBlock() }
    val MELTDOWN_BLOCK: DeferredBlock<Block> = MOD_BLOCKS.registerBlock("meltdown") { MeltdownTurretBlock() }

    fun register() {
        MOD_BLOCKS.register(MOD_BUS)
    }
}