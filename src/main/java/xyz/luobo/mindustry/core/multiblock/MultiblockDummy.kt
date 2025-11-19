package xyz.luobo.mindustry.core.multiblock

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class MultiblockDummy(properties: Properties) : Block(properties) {
    override fun getRenderShape(state: BlockState) = RenderShape.INVISIBLE

    public fun use(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player
    ) {
        // 自动转发到主控
        MultiblockManager.findController(level, pos).let { controllerPos ->
            //            level.getBlockState(controllerPos).use(level, player, hand, hit)
        }
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        return super.useWithoutItem(state, level, pos, player, hitResult)
    }

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult
    ): ItemInteractionResult {
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult)
    }
}