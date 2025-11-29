package xyz.luobo.mindustry.common.machines.graphite_press

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import xyz.luobo.mindustry.common.ModBlockEntityTypes
import xyz.luobo.mindustry.core.multiblock.IMultiblockControllerBlockEntity

class GraphitePressBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(ModBlockEntityTypes.GRAPHITE_PRESS_BLOCK_ENTITY.get(), pos, state),
    IMultiblockControllerBlockEntity {

    override fun getMultiblockSize(): Int = 2
    override fun doUseWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ) {
        this.doUseWithoutItem(state, level, pos, player, hitResult)
    }

    override fun doUseItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult
    ) {
        this.doUseItemOn(stack, state, level, pos, player, hand, hitResult)
    }
}