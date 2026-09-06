package xyz.luobo.mturrets.common.power

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.core.structure.Blueprint
import xyz.luobo.mturrets.core.structure.BlueprintAnchorBlock

/**
 * 电力节点(ADR-0007,无线链路修订 #69):1×1 蓝图锚点。放置后 [BlueprintAnchorBlock.afterFormed]
 * 自动补链;服务端 ticker 刷新供电比例供激光健康色。空手右键接线交互见 [PowerLinkInteraction]。
 */
class PowerNodeBlock : BlueprintAnchorBlock(structureProperties()) {

    override val blueprint: Blueprint = Blueprint(emptyList())

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = PowerNodeBE(pos, state)

    override fun <E : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<E>
    ): BlockEntityTicker<E>? {
        if (type !== ModBlockEntityTypes.POWER_NODE.get()) return null
        val ticker = BlockEntityTicker<PowerNodeBE> { lvl, _, _, be ->
            if (!lvl.isClientSide) be.tickServer()
        }
        @Suppress("UNCHECKED_CAST")
        return ticker as BlockEntityTicker<E>
    }

    /** 结构成型(下一 tick)后自动补链。 */
    override fun afterFormed(level: ServerLevel, pos: BlockPos, state: BlockState) {
        (level.getBlockEntity(pos) as? PowerNodeBE)?.autolink()
    }

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC

    companion object {
        private val CODEC: MapCodec<PowerNodeBlock> = simpleCodec { PowerNodeBlock() }
    }
}