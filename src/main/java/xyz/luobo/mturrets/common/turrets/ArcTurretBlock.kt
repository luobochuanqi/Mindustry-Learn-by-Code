package xyz.luobo.mturrets.common.turrets

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.core.turret.block.BaseTurretBlock

/**
 * Arc 电弧炮台方块
 * 静态方块模型渲染(贴图来自 Mindustry 开源仓库,见 README 资产出处)
 */
class ArcTurretBlock : BaseTurretBlock<ArcTurretBlockEntity>(
    Properties.of()
        .strength(2.0f)
        .requiresCorrectToolForDrops()
        .noOcclusion()
) {

    override fun getBlockEntityType(): BlockEntityType<ArcTurretBlockEntity> {
        return ModBlockEntityTypes.ARC_BLOCK_ENTITY.get()
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return ArcTurretBlockEntity(pos, state)
    }

    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.MODEL
    }

    companion object {
        val CODEC: MapCodec<ArcTurretBlock> = simpleCodec { ArcTurretBlock() }
    }

    override fun codec(): MapCodec<out BaseEntityBlock> {
        return CODEC
    }
}