package xyz.luobo.mturrets.common.turrets

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
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.common.ModBlocks
import xyz.luobo.mturrets.core.structure.Blueprint
import xyz.luobo.mturrets.core.structure.BlueprintAnchorBlock

/**
 * Scatter(蓝图 2×2,#34):角锚点 +X/+Z 生长(#26 约定),3 成员格为基座外观结构块;
 * 静态基座四格各用一角模型 + 成员按偏移 variant 做 y 旋转(#34 spec 渲染决策)。
 * 交互与 Duo 同语义:可倒出手持物灌 Coolant 内罐(水);手持弹药右键整堆入仓(超 cap 拒收);
 * 无取出通道(单位账不存物理物品,拆除按倍率折回散落,ADR-0009);成员格交互经管线代理回锚点。
 */
class ScatterBlock : BlueprintAnchorBlock(structureProperties()) {

    override val blueprint: Blueprint = Blueprint(
        listOf(
            Blueprint.MemberSpec(BlockPos(1, 0, 0)) { ModBlocks.SCATTER_STRUCTURAL.get().defaultBlockState() },
            Blueprint.MemberSpec(BlockPos(0, 0, 1)) { ModBlocks.SCATTER_STRUCTURAL.get().defaultBlockState() },
            Blueprint.MemberSpec(BlockPos(1, 0, 1)) { ModBlocks.SCATTER_STRUCTURAL.get().defaultBlockState() }
        )
    )

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = ScatterTurretBE(pos, state)

    override fun <E : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<E>
    ): BlockEntityTicker<E>? {
        if (level.isClientSide || type !== ModBlockEntityTypes.SCATTER_BLOCK_ENTITY.get()) return null
        val ticker = BlockEntityTicker<ScatterTurretBE> { _, _, _, be -> be.tickServer() }
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
        val turret = level.getBlockEntity(pos) as? ScatterTurretBE
            ?: return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        // 容器语义先行:可倒出液体的手持物 → 灌 Coolant 内罐(换桶由 FluidUtil 完成)
        if (FluidUtil.interactWithFluidHandler(player, hand, turret.fluidCapability)) {
            return ItemInteractionResult.CONSUME
        }
        // 手持弹药右键装弹:整堆折算入仓,超 cap 整堆拒收(物品原样保留)
        if (turret.ammoTypeFor(stack.item) != null) {
            return if (turret.tryLoadAmmo(stack)) {
                stack.shrink(stack.count)
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
    ): InteractionResult = InteractionResult.PASS

    companion object {
        @JvmStatic
        val CODEC: MapCodec<ScatterBlock> = simpleCodec { ScatterBlock() }
    }

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC
}