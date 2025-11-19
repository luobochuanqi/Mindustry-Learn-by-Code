package xyz.luobo.mindustry.core.multiblock

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import xyz.luobo.mindustry.core.registry.MachineRegistry

abstract class MultiblockController(properties: Properties) : BaseEntityBlock(properties) {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        // 从注册表获取定义，创建对应 BlockEntity
        return MachineRegistry.get(blockDefinitionId)?.blockEntity?.invoke(pos, state)
    }

    // 自动处理结构形成/解除
    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, isMoving: Boolean) {
        super.onPlace(state, level, pos, oldState, isMoving)
        MultiblockManager.attemptFormStructure(level, pos, this)
    }

    override fun onDestroyedByPlayer(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        willHarvest: Boolean,
        fluid: FluidState
    ): Boolean {
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid)
    }

    override fun onDestroyedByPushReaction(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        pushDirection: Direction,
        fluid: FluidState
    ) {
        super.onDestroyedByPushReaction(state, level, pos, pushDirection, fluid)
    }

    fun onDestroyed(state: BlockState, level: Level, pos: BlockPos) {
        MultiblockManager.breakStructure(level, pos, this)
    }

    // 子类只需指定注册ID
    protected abstract val blockDefinitionId: ResourceLocation
}