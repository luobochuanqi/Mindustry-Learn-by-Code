package xyz.luobo.mturrets.common.machines.kiln

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.neoforged.neoforge.fluids.FluidUtil
import net.neoforged.neoforge.items.ItemHandlerHelper
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.core.structure.Blueprint
import xyz.luobo.mturrets.core.structure.BlueprintAnchorBlock

/**
 * 窑炉(蓝图管线 1×1,ADR-0003/0006/0008):偏移集为空,放置/拆除/回滚全由管线兜住。
 * 交互无 GUI:可倒出液体的手持物右键灌内罐、配方物品右键放入、空手右键取出(产出优先)。
 */
class KilnBlock : BlueprintAnchorBlock(structureProperties()) {

    override val blueprint: Blueprint = Blueprint(emptyList())

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = KilnBE(pos, state)

    override fun <E : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<E>
    ): BlockEntityTicker<E>? {
        if (type !== ModBlockEntityTypes.KILN.get()) return null
        // 服务端:推进加工;客户端:驱动运转 hum(#57)。MachineHum 按 HummingMachine 接口统一,
        // 客户端类在 common 文件走 FQN(与 visual 注册惯例一致)。
        val ticker = BlockEntityTicker<KilnBE> { lvl, _, _, be ->
            if (lvl.isClientSide) xyz.luobo.mturrets.client.audio.MachineHum.tick(be)
            else be.tickServer()
        }
        @Suppress("UNCHECKED_CAST")
        return ticker as BlockEntityTicker<E>
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
        if (level.isClientSide) return ItemInteractionResult.SUCCESS
        val kiln = level.getBlockEntity(pos) as? KilnBE
            ?: return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        // 容器语义先行:水桶等可倒出液体的手持物 → 灌内罐(换桶由 FluidUtil 完成)
        if (FluidUtil.interactWithFluidHandler(player, hand, kiln.fluidCapability)) {
            return ItemInteractionResult.CONSUME
        }
        if (kiln.isBufferItem(stack)) {
            val rest = ItemHandlerHelper.insertItem(kiln.itemCapability, stack, false)
            return if (rest.count < stack.count) {
                stack.shrink(stack.count - rest.count)
                ItemInteractionResult.CONSUME
            } else {
                ItemInteractionResult.FAIL
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (level.isClientSide) return InteractionResult.SUCCESS
        val kiln = level.getBlockEntity(pos) as? KilnBE ?: return InteractionResult.PASS
        // 空手右键 = 取出(产出优先);无可取 → PASS
        val extracted = kiln.takeBufferStack() ?: return InteractionResult.PASS
        ItemHandlerHelper.giveItemToPlayer(player, extracted)
        return InteractionResult.CONSUME
    }

    companion object {
        @JvmStatic
        val CODEC: MapCodec<KilnBlock> = simpleCodec { KilnBlock() }
    }

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC
}
