package xyz.luobo.mturrets.common.power

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mturrets.core.structure.Blueprint
import xyz.luobo.mturrets.core.structure.BlueprintAnchorBlock

/**
 * 电力节点(ADR-0007):纯导线构件、零储能;经蓝图管线 1×1 锚点放置,无 ticker、
 * 无交互、无 capability(节点读不到储能——电能只住在电池与耗电结构本地缓冲)。
 */
class PowerNodeBlock : BlueprintAnchorBlock(structureProperties()) {

    override val blueprint: Blueprint = Blueprint(emptyList())

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = PowerNodeBE(pos, state)

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC

    companion object {
        private val CODEC: MapCodec<PowerNodeBlock> = simpleCodec { PowerNodeBlock() }
    }
}