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
 * Duo 炮台方块
 * 继承 BaseEntityBlock 以支持 BlockEntity 渲染和 tick
 */
class DuoTurretBlock : BaseEntityBlock(
    Properties.of()
        .strength(2.0f)
        .requiresCorrectToolForDrops()
        .noOcclusion()
) {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return DuoTurretBlockEntity(pos, state)
    }

    companion object {
        val CODEC: MapCodec<DuoTurretBlock> = simpleCodec { DuoTurretBlock() }

        /**
         * BlockEntityTicker 实现
         * 每 tick 调用 BlockEntity 的 tickServer 方法
         */
        private val TICKER: BlockEntityTicker<DuoTurretBlockEntity> =
            BlockEntityTicker { level, pos, state, blockEntity ->
                if (!level.isClientSide && blockEntity is DuoTurretBlockEntity) {
                    blockEntity.tickServer(level, pos, state)
                }
            }
    }

    override fun codec(): MapCodec<out BaseEntityBlock> {
        return CODEC
    }

    /**
     * 返回渲染形状为 ENTITYBLOCK_ANIMATED，这样 BlockEntityRenderer 才会被调用
     */
    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.ENTITYBLOCK_ANIMATED
    }

    /**
     * 注册 BlockEntityTicker，使炮台能够每 tick 更新
     */
    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        return if (type === ModBlockEntityTypes.DUO_BLOCK_ENTITY.get()) {
            @Suppress("UNCHECKED_CAST")
            TICKER as BlockEntityTicker<T>
        } else {
            null
        }
    }
}