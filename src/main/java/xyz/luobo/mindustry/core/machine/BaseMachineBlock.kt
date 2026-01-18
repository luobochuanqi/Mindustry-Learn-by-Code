package xyz.luobo.mindustry.core.machine

import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

abstract class BaseMachineBlock<T : BaseMachineBE>(
    properties: Properties
) : BaseEntityBlock(properties) {
    // 强制子类指定渲染类型 (通常是 MODEL)
    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    // 泛型 Ticker 适配
    override fun <E : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<E>
    ): BlockEntityTicker<E>? {
        // 仅服务端 Tick，客户端通常不需要运行逻辑，只需渲染
        if (level.isClientSide) return null

        // 验证 BE 类型是否匹配
        return createTickerHelper(type, getBlockEntityType()) { be ->
            (be as? BaseMachineBE)?.tickServer()
        }
    }

    // 子类必须提供对应的 BlockEntityType
    abstract fun getBlockEntityType(): BlockEntityType<T>

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

    // TODO("实现 onRemove 以防止破坏方块时物品消失 (drop contents)")
}