package xyz.luobo.mindustry.common.machines.graphite_press

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mindustry.core.multiblock.MultiblockControllerBlock

class GraphitePressBlock : MultiblockControllerBlock(
    Properties.of()
        .strength(3.0f)
) {

    companion object {
        @JvmStatic
        val CODEC: MapCodec<GraphitePressBlock> = simpleCodec(
            { GraphitePressBlock() },
        )
    }

    override fun codec(): MapCodec<out BaseEntityBlock?> {
        return CODEC
    }

    override fun newBlockEntity(
        pos: BlockPos,
        state: BlockState
    ): BlockEntity? {
        return GraphitePressBlockEntity(pos, state)
    }
}