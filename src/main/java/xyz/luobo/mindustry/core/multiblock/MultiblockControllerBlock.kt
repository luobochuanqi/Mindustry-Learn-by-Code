package xyz.luobo.mindustry.core.multiblock

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
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

    private val multiblockManager = MultiblockManager()

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, movedByPiston: Boolean) {
        super.onPlace(state, level, pos, oldState, movedByPiston)

        // 尝试形成多方块结构
        if (!level.isClientSide) {
            val machineDef = xyz.luobo.mindustry.core.registry.MachineRegistry.getDefinitionByBlock(this)
            if (machineDef != null) {
                multiblockManager.attemptFormStructure(level, pos, machineDef.size)
            }
        }
    }

    override fun onDestroyedByPlayer(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        willHarvest: Boolean,
        fluid: FluidState
    ): Boolean {
        // 破坏多方块结构
        if (!level.isClientSide) {
            val machineDef = xyz.luobo.mindustry.core.registry.MachineRegistry.getDefinitionByBlock(this)
            if (machineDef != null) {
                multiblockManager.breakStructure(level, pos, machineDef.size)
            }
        }

        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid)
    }

    override fun onDestroyedByPushReaction(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        pushDirection: Direction,
        fluid: FluidState
    ) {
        // 破坏多方块结构
        if (!level.isClientSide) {
            val machineDef = xyz.luobo.mindustry.core.registry.MachineRegistry.getDefinitionByBlock(this)
            if (machineDef != null) {
                multiblockManager.breakStructure(level, pos, machineDef.size)
            }
        }

        super.onDestroyedByPushReaction(state, level, pos, pushDirection, fluid)
    }
}