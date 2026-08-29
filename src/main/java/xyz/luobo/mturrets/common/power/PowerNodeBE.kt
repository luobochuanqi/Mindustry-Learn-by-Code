package xyz.luobo.mturrets.common.power

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.core.power.PowerAnchorBE

/** 电力节点 BE(ADR-0007):零状态、零 capability,图成员身份由 [PowerAnchorBE] 钩子收口。 */
class PowerNodeBE(pos: BlockPos, state: BlockState) :
    PowerAnchorBE(ModBlockEntityTypes.POWER_NODE.get(), pos, state)