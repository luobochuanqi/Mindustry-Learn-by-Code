package xyz.luobo.mindustry.common.turrets

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * Arc 电弧炮台方块
 */
class ArcTurretBlock : Block(
    Properties.of()
        .strength(2.0f)
        .requiresCorrectToolForDrops()
), EntityBlock {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return ArcTurretBlockEntity(pos, state)
    }
}