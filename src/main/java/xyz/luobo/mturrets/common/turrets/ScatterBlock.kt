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
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.neoforged.neoforge.fluids.FluidUtil
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.common.ModBlocks
import xyz.luobo.mturrets.core.structure.Blueprint
import xyz.luobo.mturrets.core.structure.BlueprintAnchorBlock

/**
 * Scatter(蓝图 2×2,#34):角锚点 +X/+Z 生长(#26 约定),3 成员格为基座外观结构块;
 * 交互与 Duo 同语义:可倒出手持物灌 Coolant 内罐(水);手持弹药右键部分入仓(按整件向下取整,#46);
 * 无取出通道(单位账不存物理物品,拆除按倍率折回散落,ADR-0009);成员格交互经管线代理回锚点。
 * 全模型资产架构(#42):方块侧渲染为空,几何全部由 BE visual 承担(Create 大水车锚点同款)。
 */
class ScatterBlock : BlueprintAnchorBlock(structureProperties()) {

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.ENTITYBLOCK_ANIMATED

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
        if (type !== ModBlockEntityTypes.SCATTER_BLOCK_ENTITY.get()) return null
        val ticker = BlockEntityTicker<ScatterTurretBE> { _, _, _, be ->
            if (level.isClientSide) be.muzzleFlash() else be.tickServer()
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
        val turret = level.getBlockEntity(pos) as? ScatterTurretBE
            ?: return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        // 容器语义先行:可倒出液体的手持物 → 灌 Coolant 内罐(换桶由 FluidUtil 完成)
        if (FluidUtil.interactWithFluidHandler(player, hand, turret.fluidCapability)) {
            return ItemInteractionResult.CONSUME
        }
        // 手持弹药右键装弹:按剩余容量向下取整到整件,部分入仓,手持堆按接受件数缩小(#46 取代 #31 整堆拒收)
        if (turret.ammoTypeFor(stack.item) != null) {
            val accepted = turret.tryLoadAmmo(stack)
            return if (accepted > 0) {
                stack.shrink(accepted)
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