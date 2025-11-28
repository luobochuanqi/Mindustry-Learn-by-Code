package xyz.luobo.mindustry.common.machines.graphite_press

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mindustry.common.ModBlockEntityTypes
import xyz.luobo.mindustry.core.multiblock.IMultiblockControllerBlockEntity

class GraphitePressBlockEntity(pos: BlockPos, state: BlockState) : IMultiblockControllerBlockEntity,
    BlockEntity(ModBlockEntityTypes.GRAPHITE_PRESS_BLOCK_ENTITY.get(), pos, state) {

}