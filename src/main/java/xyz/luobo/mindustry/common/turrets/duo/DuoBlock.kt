package xyz.luobo.mindustry.common.turrets.duo

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class DuoBlock : BaseEntityBlock(Properties.of().noOcclusion()) {

    companion object {
        @JvmStatic
        val CODEC: MapCodec<DuoBlock> = simpleCodec({ DuoBlock() })
    }

    override fun codec(): MapCodec<out BaseEntityBlock?> {
        return CODEC
    }

    override fun newBlockEntity(
        pos: BlockPos,
        state: BlockState
    ): BlockEntity {
        return DuoBE(pos, state)
    }

    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.ENTITYBLOCK_ANIMATED
    }
}