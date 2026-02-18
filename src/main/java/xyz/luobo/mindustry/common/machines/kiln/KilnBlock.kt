package xyz.luobo.mindustry.common.machines.kiln

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import xyz.luobo.mindustry.Mindustry
import xyz.luobo.mindustry.common.ModBlockEntityTypes
import xyz.luobo.mindustry.common.items.DebugBaconItem
import xyz.luobo.mindustry.core.machine.BaseMachineBlock

class KilnBlock : BaseMachineBlock<KilnBE>(Properties.of()) {

    companion object {
        @JvmStatic
        val CODEC: MapCodec<KilnBlock> = simpleCodec { KilnBlock() }
    }

    override fun codec(): MapCodec<out BaseEntityBlock?> {
        return CODEC
    }

    override fun getBlockEntityType(): BlockEntityType<KilnBE> {
        return ModBlockEntityTypes.KILN_BLOCK_ENTITY.get()
    }

    override fun newBlockEntity(
        pos: BlockPos,
        state: BlockState
    ): BlockEntity {
        return KilnBE(pos = pos, state = state)
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
        if (level.isClientSide)
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult)

        // 检查玩家是否手持 DebugBacon
        val heldItem = player.getItemInHand(hand)
        if (heldItem.item is DebugBaconItem) {
            // 获取方块实体
            val be = level.getBlockEntity(pos)
            if (be is KilnBE) {
                // 输出调试信息
                outputDebugInfo(be)
                return ItemInteractionResult.SUCCESS
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult)
    }

    /**
     * 输出方块实体的调试信息
     */
    private fun outputDebugInfo(be: KilnBE) {
        Mindustry.LOGGER.info("========== Kiln Debug Info ==========")
        Mindustry.LOGGER.info("Position: ${be.blockPos}")
        Mindustry.LOGGER.info("Working: ${be.isWorking}")
        Mindustry.LOGGER.info("Progress: ${be.progress}/${be.maxProgress}")
        Mindustry.LOGGER.info("Energy: ${be.energyCapability?.currentEnergy}/${be.energyCapability?.energyCapacity}")

        // 输出物品槽信息
        val itemCap = be.itemCapability
        Mindustry.LOGGER.info("Item Slots (${itemCap.slotCount}):")
        for (i in 0 until itemCap.slotCount) {
            val stack = itemCap.getStack(i)
            val slotType = when (i) {
                KilnBE.INPUT_SLOT_1 -> "INPUT_1"
                KilnBE.INPUT_SLOT_2 -> "INPUT_2"
                KilnBE.OUTPUT_SLOT -> "OUTPUT"
                else -> "UNKNOWN"
            }
            if (stack.isEmpty) {
                Mindustry.LOGGER.info("  [$i] [$slotType]: EMPTY")
            } else {
                Mindustry.LOGGER.info("  [$i] [$slotType]: ${stack.item} x${stack.count}")
            }
        }

        Mindustry.LOGGER.info("=======================================")
    }
}