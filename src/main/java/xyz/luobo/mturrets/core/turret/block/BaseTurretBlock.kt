package xyz.luobo.mturrets.core.turret.block

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mturrets.core.turret.entity.BaseTurretBlockEntity

/**
 * 炮台方块共享基类
 * 三座炮台(Duo/Arc/Meltdown)共用的方块逻辑:实体创建与服务端 ticker。
 * 渲染为静态方块模型(RenderShape.MODEL)。
 *
 * @param properties 方块属性(强度、挖掘工具等)由子类提供
 */
abstract class BaseTurretBlock<T : BaseTurretBlockEntity>(
    properties: Properties
) : BaseEntityBlock(properties) {

    // BaseEntityBlock 默认 INVISIBLE;炮台全部走静态方块模型
    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.MODEL
    }

    /** 子类提供对应的方块实体类型 */
    protected abstract fun getBlockEntityType(): BlockEntityType<T>

    override fun <E : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<E>
    ): BlockEntityTicker<E>? {
        // 炮台逻辑只在服务端 tick
        if (level.isClientSide) return null

        return createTickerHelper(type, getBlockEntityType()) { be ->
            be.tickServer(level, be.blockPos, state)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <E : BlockEntity, A : BlockEntity> createTickerHelper(
        actualType: BlockEntityType<E>,
        targetType: BlockEntityType<A>,
        ticker: (A) -> Unit
    ): BlockEntityTicker<E>? {
        return if (targetType == actualType) {
            BlockEntityTicker { _, _, _, be -> ticker(be as A) }
        } else null
    }
}