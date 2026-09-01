package xyz.luobo.mturrets.common.power

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
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
 * 电源方块(#49,调试用):1×1 蓝图锚点,服务端 ticker 每 tick 向电网申报常量产量。
 * 无交互、无 capability、无配方(创造标签可达,见 ModTabs)。
 */
class PowerSourceBlock : BlueprintAnchorBlock(structureProperties()) {

    override val blueprint: Blueprint = Blueprint(emptyList())

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = PowerSourceBE(pos, state)

    override fun <E : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<E>
    ): BlockEntityTicker<E>? {
        if (type !== ModBlockEntityTypes.POWER_SOURCE.get()) return null
        val ticker = BlockEntityTicker<PowerSourceBE> { lvl, _, _, be ->
            if (!lvl.isClientSide) be.tickServer()
        }
        @Suppress("UNCHECKED_CAST")
        return ticker as BlockEntityTicker<E>
    }

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC

    companion object {
        private val CODEC: MapCodec<PowerSourceBlock> = simpleCodec { PowerSourceBlock() }
    }
}
