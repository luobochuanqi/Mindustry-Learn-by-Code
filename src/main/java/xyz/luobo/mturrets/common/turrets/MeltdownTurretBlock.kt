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
  * LEGACY: Meltdown 翻新期方块,换代时随 MeltdownTurret 一并重建。
 * Meltdown 重型激光炮台方块
 * 静态方块模型渲染(贴图来自 Mindustry 开源仓库,见 README 资产出处)
 */
class MeltdownTurretBlock : BaseTurretBlock<MeltdownTurretBlockEntity>(
    Properties.of()
        .strength(3.0f)
        .requiresCorrectToolForDrops()
        .noOcclusion()
) {

    override fun getBlockEntityType(): BlockEntityType<MeltdownTurretBlockEntity> {
        return ModBlockEntityTypes.MELTDOWN_BLOCK_ENTITY.get()
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return MeltdownTurretBlockEntity(pos, state)
    }

    companion object {
        val CODEC: MapCodec<MeltdownTurretBlock> = simpleCodec { MeltdownTurretBlock() }
    }

    override fun codec(): MapCodec<out BaseEntityBlock> {
        return CODEC
    }
}