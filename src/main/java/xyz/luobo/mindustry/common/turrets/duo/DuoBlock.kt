package xyz.luobo.mindustry.common.turrets.duo

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mindustry.common.ModBlockEntityTypes

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

    override fun <E : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<E>
    ): BlockEntityTicker<E>? {
        if (level.isClientSide) return null

        // 验证 BE 类型是否匹配
        return createTickerHelper(type, getBlockEntityType()) { level, pos, state, be ->
            be.tickServer(level, pos, state, be)
        }
    }

    private fun getBlockEntityType(): BlockEntityType<DuoBE> {
        return ModBlockEntityTypes.DUO_Block_Entity.get()
    }
}