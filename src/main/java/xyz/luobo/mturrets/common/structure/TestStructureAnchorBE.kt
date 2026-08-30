package xyz.luobo.mturrets.common.structure

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.entity.BlockEntity
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.core.capability.impl.ItemCapabilityImpl
import xyz.luobo.mturrets.core.structure.Blueprint
import xyz.luobo.mturrets.core.structure.BlueprintAnchor
import xyz.luobo.mturrets.core.structure.BlueprintAnchorBlock

/**
 * 临时测试锚点 BE(骨架验收用):最简 [BlueprintAnchor] 实现。
 * 带一个物品槽,供"成员格能力路由解析回锚点"的外部断言;真内容 BE(#33/#34)另行实现本契约。
 */
class TestStructureAnchorBE(pos: BlockPos, state: BlockState) :
    BlockEntity(ModBlockEntityTypes.TEST_STRUCTURE_ANCHOR_BE.get(), pos, state), BlueprintAnchor {

    override val currentBlueprint: Blueprint
        get() = (blockState.block as BlueprintAnchorBlock).blueprint

    override val itemCapability: ItemCapabilityImpl = ItemCapabilityImpl(1)
}