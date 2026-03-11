package xyz.luobo.mindustry.common.turrets

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

/**
 * Meltdown 重型激光炮台方块
 */
class MeltdownTurretBlock : BaseEntityBlock(
    Properties.of()
        .strength(3.0f)
        .requiresCorrectToolForDrops()
        .noOcclusion()
) {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return MeltdownTurretBlockEntity(pos, state)
    }

    companion object {
        val CODEC: MapCodec<MeltdownTurretBlock> = simpleCodec { MeltdownTurretBlock() }

        private val TICKER: BlockEntityTicker<MeltdownTurretBlockEntity> =
            BlockEntityTicker { level, pos, state, blockEntity ->
                if (!level.isClientSide && blockEntity is MeltdownTurretBlockEntity) {
                    blockEntity.tickServer(level, pos, state)
                }
            }
    }

    override fun codec(): MapCodec<out BaseEntityBlock> {
        return CODEC
    }

    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.ENTITYBLOCK_ANIMATED
    }

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        return if (type === ModBlockEntityTypes.MELTDOWN_BLOCK_ENTITY.get()) {
            @Suppress("UNCHECKED_CAST")
            TICKER as BlockEntityTicker<T>
        } else {
            null
        }
    }
}