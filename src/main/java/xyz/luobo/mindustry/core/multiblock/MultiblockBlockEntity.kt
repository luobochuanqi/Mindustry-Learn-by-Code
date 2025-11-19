package xyz.luobo.mindustry.core.multiblock

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

class MultiblockBlockEntity(type: BlockEntityType<MultiblockBlockEntity>, pos: BlockPos, state: BlockState) :
    BlockEntity(type, pos, state) {
}