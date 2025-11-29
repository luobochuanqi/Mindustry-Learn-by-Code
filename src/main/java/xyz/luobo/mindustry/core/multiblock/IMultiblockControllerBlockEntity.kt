package xyz.luobo.mindustry.core.multiblock

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

/**
 * 用于被 BlockEntity 实现，标识这是一个多方块结构的控制器
 */
interface IMultiblockControllerBlockEntity {
    /**
     * 获取多方块结构的大小
     */
    fun getMultiblockSize(): Int = 1

    fun doUseWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult)

    fun doUseItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult
    )

    /**
     * 当多方块结构形成时调用
     */
    fun onMultiblockFormed() {}

    /**
     * 当多方块结构被破坏时调用
     */
    fun onMultiblockBroken() {}
}