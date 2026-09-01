package xyz.luobo.mturrets.common.machines.drill

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
import xyz.luobo.mturrets.common.ModBlocks
import xyz.luobo.mturrets.core.structure.Blueprint
import xyz.luobo.mturrets.core.structure.BlueprintAnchorBlock

/**
 * 机械钻头(蓝图 2×2,ADR-0003/0008):角锚点 +X/+Z 生长(#26 约定),3 成员格为钻头外观结构块。
 * 交互无 GUI:可倒出液体的手持物右键灌内罐(水加成);空手右键取 Buffer。
 * 无配方输入 → 不放料通道(区别于窑炉,#35 spec 定案)。
 */
class DrillBlock : BlueprintAnchorBlock(structureProperties()) {

    override val blueprint: Blueprint = Blueprint(
        listOf(
            Blueprint.MemberSpec(BlockPos(1, 0, 0)) { ModBlocks.DRILL_STRUCTURAL.get().defaultBlockState() },
            Blueprint.MemberSpec(BlockPos(0, 0, 1)) { ModBlocks.DRILL_STRUCTURAL.get().defaultBlockState() },
            Blueprint.MemberSpec(BlockPos(1, 0, 1)) { ModBlocks.DRILL_STRUCTURAL.get().defaultBlockState() }
        )
    )

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = DrillBE(pos, state)

    override fun <E : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<E>
    ): BlockEntityTicker<E>? {
        if (type !== ModBlockEntityTypes.DRILL.get()) return null
        // 服务端:推进采集;客户端:驱动运转 hum(#57)。MachineHum 按 HummingMachine 接口统一,
        // 客户端类在 common 文件走 FQN(与 visual 注册惯例一致)。
        val ticker = BlockEntityTicker<DrillBE> { lvl, _, _, be ->
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
        val drill = level.getBlockEntity(pos) as? DrillBE
            ?: return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        // 容器语义先行:水桶等可倒出液体的手持物 → 灌内罐(换桶由 FluidUtil 完成)
        if (FluidUtil.interactWithFluidHandler(player, hand, drill.fluidCapability)) {
            return ItemInteractionResult.CONSUME
        }
        // 空手右键 = 取出(spec)。1.21.1 服务端只在 useItemOn 返回 PASS_TO_DEFAULT 时才
        // fallback 到 useWithoutItem,FAIL 会连空手一起短路——取出必须在空手分支内自己消费。
        if (stack.isEmpty) {
            val extracted = drill.takeBufferStack()
                ?: return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
            ItemHandlerHelper.giveItemToPlayer(player, extracted)
            return ItemInteractionResult.CONSUME
        }
        // 手持其它物品:无放料通道,拒绝并整体短路(避免落回 useWithoutItem 误取出)
        return ItemInteractionResult.FAIL
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (level.isClientSide) return InteractionResult.SUCCESS
        val drill = level.getBlockEntity(pos) as? DrillBE ?: return InteractionResult.PASS
        val extracted = drill.takeBufferStack() ?: return InteractionResult.PASS
        ItemHandlerHelper.giveItemToPlayer(player, extracted)
        return InteractionResult.CONSUME
    }

    companion object {
        @JvmStatic
        val CODEC: MapCodec<DrillBlock> = simpleCodec { DrillBlock() }
    }

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC
}