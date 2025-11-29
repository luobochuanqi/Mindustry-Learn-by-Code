package xyz.luobo.mindustry.common.machines.graphite_press

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mindustry.core.multiblock.MultiblockDummyBlock

class GraphitePressDummyBlock : MultiblockDummyBlock(Properties.of().strength(2.0f)), EntityBlock {
    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return GraphitePressDummyBlockEntity(pos, state)
    }
}