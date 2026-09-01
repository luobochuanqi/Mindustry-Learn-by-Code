package xyz.luobo.mturrets.common.power

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
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
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.core.structure.Blueprint
import xyz.luobo.mturrets.core.structure.BlueprintAnchorBlock

/**
 * 燃烧发电机(#56):1×1 蓝图锚点(空偏移集,管线兜放置/拆除/回滚)。无 GUI:
 * 右键持煤放燃料(总量 8 强制);空手右键无操作(PASS)。客户端 ticker 在燃烧中发
 * 原版火焰粒子(零新资产,spec 视觉面)。
 */
class CombustionGeneratorBlock : BlueprintAnchorBlock(structureProperties()) {

    override val blueprint: Blueprint = Blueprint(emptyList())

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        CombustionGeneratorBE(pos, state)

    override fun <E : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<E>
    ): BlockEntityTicker<E>? {
        if (type !== ModBlockEntityTypes.COMBUSTION_GENERATOR.get()) return null
        val ticker = BlockEntityTicker<CombustionGeneratorBE> { lvl, pos, st, be ->
            if (lvl.isClientSide) tickParticles(lvl, pos) else be.tickServer()
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
        val be = level.getBlockEntity(pos) as? CombustionGeneratorBE
            ?: return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        return if (be.insertFuel(stack)) {
            ItemInteractionResult.CONSUME
        } else {
            ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        }
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        // 空手无操作(无取料通道,燃料已投入即不可取回)
        return InteractionResult.PASS
    }

    /** 燃烧中发火焰粒子(客户端 ticker);概率门控防过密。 */
    private fun tickParticles(level: Level, pos: BlockPos) {
        if (!level.isClientSide) return
        val be = level.getBlockEntity(pos) as? CombustionGeneratorBE ?: return
        if (!be.isBurning) return
        if (level.random.nextInt(3) != 0) return
        val x = pos.x + level.random.nextDouble()
        val y = pos.y + 0.3 + level.random.nextDouble() * 0.4
        val z = pos.z + level.random.nextDouble()
        level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.15, 0.0)
    }

    companion object {
        @JvmStatic
        val CODEC: MapCodec<CombustionGeneratorBlock> = simpleCodec { CombustionGeneratorBlock() }
    }

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC
}
