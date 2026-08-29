package xyz.luobo.mturrets.core.power

import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mturrets.core.capability.IItemCapability
import xyz.luobo.mturrets.core.structure.Blueprint
import xyz.luobo.mturrets.core.structure.BlueprintAnchor
import xyz.luobo.mturrets.core.structure.BlueprintAnchorBlock

/**
 * 电网构件锚点(1×1,ADR-0003):蓝图/拆除契约的共用空实现——电网构件无 Buffer
 * 内容物,拆机只掉控制器物品,电量随块消失(不物品化搬运)。
 */
abstract class PowerAnchorBE(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : PowerMemberBE(type, pos, state), BlueprintAnchor {

    override val currentBlueprint: Blueprint
        get() = (blockState.block as BlueprintAnchorBlock).blueprint

    override val itemCapability: IItemCapability? = null

    override fun contentsToScatter(destroyed: Boolean): List<ItemStack> = emptyList()
}