package xyz.luobo.mindustry.common.blockEntities

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mindustry.common.ModBlockEntities

class GraphitePressController(pos: BlockPos, state: BlockState):
    BlockEntity(ModBlockEntities.GRAPHITE_PRESS_CONTROLLER.get(), pos, state) {

}