package xyz.luobo.mindustry.core.multiblock

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState

/**
 * 多方快控制器方块
 * 只需实现
 * 链接 BlockEntityType
 * 实现玩家右键逻辑
 * 调用 MultiblockManager 实现控制多方快的生命周期
 */
abstract class MultiblockControllerBlock(properties: Properties) :
    BaseEntityBlock(properties) {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        TODO("Not yet implemented")
    }

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, movedByPiston: Boolean) {
        TODO("Not yet implemented")
    }

    override fun onDestroyedByPlayer(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        willHarvest: Boolean,
        fluid: FluidState
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun onDestroyedByPushReaction(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        pushDirection: Direction,
        fluid: FluidState
    ) {
        TODO("Not yet implemented")
    }
}