package xyz.luobo.mindustry.core.turret

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

/**
 * 炮台方块基类
 * 提供炮台方块的通用功能，包括方块实体创建、Ticker 注册等
 *
 * @param T 炮台方块实体类型
 * @param properties 方块属性
 */
abstract class BaseTurretBlock<T : BaseTurretBE>(
    properties: Properties
) : BaseEntityBlock(properties) {

    /**
     * 获取方块实体类型
     * 子类必须实现此方法
     */
    protected abstract fun getBlockEntityType(): BlockEntityType<T>

    /**
     * 创建方块实体
     */
    override fun newBlockEntity(
        pos: BlockPos,
        state: BlockState
    ): BlockEntity? {
        val blockEntityType = getBlockEntityType()
        return blockEntityType.create(pos, state)
    }

    /**
     * 获取渲染形状
     * 默认返回 ENTITYBLOCK_ANIMATED 以支持 GeckoLib 动画
     * 子类可以重写此方法
     */
    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.ENTITYBLOCK_ANIMATED
    }

    /**
     * 获取方块实体 Ticker
     * 默认只在服务端运行 tick 逻辑
     * 子类可以重写此方法以支持客户端渲染
     */
    override fun <E : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<E>
    ): BlockEntityTicker<E>? {
        if (level.isClientSide) {
            return null
        }

        return createTickerHelper(type, getBlockEntityType()) { level, pos, state, be ->
            @Suppress("UNCHECKED_CAST")
            (be as T).tickServer(level, pos, state)
        }
    }
}