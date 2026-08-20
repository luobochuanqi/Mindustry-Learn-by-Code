package xyz.luobo.mturrets.common.turrets

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.core.turret.block.BaseTurretBlock

/**
 * Duo 炮台方块
 * 使用 GeckoLib 动画渲染,共享 [BaseTurretBlock] 的 ticker/渲染形状逻辑
 */
class DuoTurretBlock : BaseTurretBlock<DuoTurretBlockEntity>(
    Properties.of()
        .strength(2.0f)
        .requiresCorrectToolForDrops()
        .noOcclusion()
) {

    override fun getBlockEntityType(): BlockEntityType<DuoTurretBlockEntity> {
        return ModBlockEntityTypes.DUO_BLOCK_ENTITY.get()
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return DuoTurretBlockEntity(pos, state)
    }

    companion object {
        val CODEC: MapCodec<DuoTurretBlock> = simpleCodec { DuoTurretBlock() }
    }

    override fun codec(): MapCodec<out BaseEntityBlock> {
        return CODEC
    }
}