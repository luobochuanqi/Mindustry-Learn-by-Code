package xyz.luobo.mindustry.common.turrets

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * Meltdown 重型激光炮台方块
 */
class MeltdownTurretBlock : Block(
    Properties.of()
        .strength(3.0f)
        .requiresCorrectToolForDrops()
), EntityBlock {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return MeltdownTurretBlockEntity(pos, state)
    }
}