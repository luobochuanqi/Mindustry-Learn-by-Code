package xyz.luobo.mindustry.common.blocks

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mindustry.common.blockEntities.GraphitePressController

class GraphitePressControllerBlock : BaseEntityBlock(Properties.of()
    .strength(2.0f)
    .requiresCorrectToolForDrops()
) {
    companion object {
        @JvmStatic
        val CODEC: MapCodec<GraphitePressControllerBlock> = simpleCodec(
            { GraphitePressControllerBlock() }
        )
    }

    override fun newBlockEntity(
        pos: BlockPos,
        state: BlockState
    ): BlockEntity? {
        return GraphitePressController(pos = pos, state = state)
    }

    override fun <T : BlockEntity?> getTicker(
        level: Level,
        state: BlockState,
        blockEntityType: BlockEntityType<T?>
    ): BlockEntityTicker<T?>? {
        return super.getTicker(level, state, blockEntityType)
    }

    override fun codec(): MapCodec<out BaseEntityBlock?> {
        return CODEC
    }
}