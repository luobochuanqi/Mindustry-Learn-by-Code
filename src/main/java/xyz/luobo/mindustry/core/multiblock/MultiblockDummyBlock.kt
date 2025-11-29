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

abstract class MultiblockDummyBlock(properties: Properties) : Block(properties) {

    // 多方快的子方块无需渲染, 只渲染控制器的 Model.
    override fun getRenderShape(state: BlockState) = RenderShape.INVISIBLE

    // 空手右键子方块时, 重定向逻辑到控制器的右键逻辑
    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (!level.isClientSide) {
            val controllerPos = findController(level, pos)
            if (controllerPos != null) {
                // 重定向到控制器
                val controllerEntity = level.getBlockEntity(controllerPos)
                if (controllerEntity is IMultiblockControllerBlockEntity) {
                    controllerEntity.doUseWithoutItem(state, level, pos, player, hitResult)
                    return InteractionResult.SUCCESS
                }
            }
        }
        return InteractionResult.PASS
    }

    // 拿着物品右键子方块时, 重定向逻辑到控制器的右键逻辑
    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult
    ): ItemInteractionResult {
        if (!level.isClientSide) {
            val controllerPos = findController(level, pos)
            if (controllerPos != null) {
                // 重定向到控制器
                val controllerEntity = level.getBlockEntity(controllerPos)
                if (controllerEntity is IMultiblockControllerBlockEntity) {
                    controllerEntity.doUseItemOn(stack, state, level, pos, player, hand, hitResult)
                    return ItemInteractionResult.SUCCESS
                }
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
    }

    // 寻找控制器方块的 BlockPos
    private fun findController(level: Level, pos: BlockPos): BlockPos? {
        // 每个子方块在放置时就应该存储着控制器的 BlockPos, 直接返回即可.
        val blockEntity = level.getBlockEntity(pos)
        if (blockEntity is MultiblockDummyBlockEntity) {
            return blockEntity.getControllerPos()
        }
        return null
    }
}