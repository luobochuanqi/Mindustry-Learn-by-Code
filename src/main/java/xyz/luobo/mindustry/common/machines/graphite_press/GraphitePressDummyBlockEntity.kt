package xyz.luobo.mindustry.common.machines.graphite_press

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mindustry.common.ModBlockEntityTypes
import xyz.luobo.mindustry.core.multiblock.MultiblockDummyBlockEntity

class GraphitePressDummyBlockEntity(pos: BlockPos, state: BlockState) :
    MultiblockDummyBlockEntity(ModBlockEntityTypes.GRAPHITE_PRESS_PART_BLOCK_ENTITY.get(), pos, state)