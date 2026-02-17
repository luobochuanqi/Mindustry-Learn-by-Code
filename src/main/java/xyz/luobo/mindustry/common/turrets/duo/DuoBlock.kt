package xyz.luobo.mindustry.common.turrets.duo

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mindustry.common.ModBlockEntityTypes
import xyz.luobo.mindustry.core.turret.BaseTurretBlock

/**
 * Duo 炮台方块
 * 基于 GeckoLib 的 3D 炮台
 */
class DuoBlock : BaseTurretBlock<DuoBE>(Properties.of().noOcclusion()) {

    companion object {
        @JvmStatic
        val CODEC: MapCodec<DuoBlock> = simpleCodec { DuoBlock() }
    }

    override fun codec(): MapCodec<out DuoBlock> {
        return CODEC
    }

    override fun getBlockEntityType(): BlockEntityType<DuoBE> =
        ModBlockEntityTypes.DUO_Block_Entity.get()

    override fun newBlockEntity(pos: BlockPos, state: BlockState): DuoBE {
        return DuoBE(pos, state)
    }
}