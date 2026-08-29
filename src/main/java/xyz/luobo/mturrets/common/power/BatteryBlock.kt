package xyz.luobo.mturrets.common.power

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mturrets.core.structure.Blueprint
import xyz.luobo.mturrets.core.structure.BlueprintAnchorBlock

/**
 * 电池(ADR-0007/#27):电网储能,1×1 蓝图锚点、80,000 FE;对外充放限速 200 FE/次,
 * 图内瞬时不限速。电量经图聚合缓存实时加减账,拆机随块消失(掉落只有控制器物品)。
 */
class BatteryBlock : BlueprintAnchorBlock(structureProperties()) {

    override val blueprint: Blueprint = Blueprint(emptyList())

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = BatteryBE(pos, state)

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC

    companion object {
        private val CODEC: MapCodec<BatteryBlock> = simpleCodec { BatteryBlock() }
    }
}