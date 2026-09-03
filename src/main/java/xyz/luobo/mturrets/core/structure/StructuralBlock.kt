package xyz.luobo.mturrets.core.structure

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult

/**
 * 成员格(ADR-0003):无 BE、无 tick;碰撞/选中整格;破坏代理回锚点。
 * 交互同样代理回锚点(玩家视角成员 = 锚点,无区别):useItemOn/useWithoutItem
 * 解码偏移找锚点块后整体转调,位置参数换锚点坐标(交互逻辑与 BE 查询都在锚点上)。
 *
 * 相对锚点的偏移编进 blockstate(0..2,偏置 1 表示 -1..1,与 [Blueprint] 跨距天花板一致;
 * vanilla 的 IntegerProperty 不允许负最小值),锚点坐标 = 本格坐标 - 解码偏移,
 * 成员格零持久化引用。
 */
open class StructuralBlock(properties: Properties) : Block(properties), MultiPosDestructionHandler {
    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(OFFSET_X, OFFSET_BIAS)
                .setValue(OFFSET_Y, OFFSET_BIAS)
                .setValue(OFFSET_Z, OFFSET_BIAS)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(OFFSET_X, OFFSET_Y, OFFSET_Z)
    }

    /**
     * 成员无物品(ADR-0003),拾取栈代理回锚点方块(Create 大水车结构块同语义):
     * Jade 悬浮成员显示结构本体图标,创造中键拾取成员也拿到控制器物品。
     * Jade 的 picked result 与创造 pick 都经 IBlockExtension 默认实现收敛到本方法。
     */
    @Suppress("DEPRECATION")
    override fun getCloneItemStack(level: LevelReader, pos: BlockPos, state: BlockState): ItemStack {
        val anchorState = level.getBlockState(pos.subtract(decodeOffset(state)))
        if (anchorState.block !is BlueprintAnchorBlock) {
            // 孤立成员/拆除中:无锚点可代理,回退默认(空栈),不崩溃
            return super.getCloneItemStack(level, pos, state)
        }
        return ItemStack(anchorState.block.asItem())
    }

    /** 裂纹代理(#42):成员格收到破坏进度 → 全结构(锚点 + 各成员)同步显示;锚点缺失(收口中)退回单格。 */
    override fun getExtraPositions(level: ClientLevel, pos: BlockPos, blockState: BlockState, progress: Int): MutableSet<BlockPos>? {
        val anchorPos = pos.subtract(decodeOffset(blockState))
        val anchor = level.getBlockState(anchorPos).block as? BlueprintAnchorBlock ?: return null
        return anchor.structureCells(anchorPos)
    }
    
    /**
     * 玩家敲击成员格的创造路径:预先无痕摘除锚点。ServerPlayerGameMode 创造分支
     * 走 removeBlock(不掉落),但成员 onRemove 代理的 destroyBlock(锚点, drop=true)
     * 不知道原 drop 标志,会多出锚点破坏粒子 + 本体掉落;锚点先变空气后,
     * 成员移除时代理守卫短路。生存挖掘不受影响(代理照常掉控制器物品)。
     */
    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        if (!level.isClientSide && player.isCreative) {
            val anchorPos = pos.subtract(decodeOffset(state))
            if (level.getBlockState(anchorPos).block is BlueprintAnchorBlock) {
                level.setBlock(anchorPos, Blocks.AIR.defaultBlockState(), 3)
            }
        }
        return super.playerWillDestroy(level, pos, state, player)
    }

    /**
     * 破坏代理:任何路径去掉成员格 → 经编码偏移找回锚点 → destroyBlock 锚点,
     * 整体拆除走锚点收口。锚点拆解清成员时锚点格已为空气(先提交后回调),
     * 守卫短路,不递归、不掉双份。
     */
    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!level.isClientSide && newState.block != state.block) {
            val anchorPos = pos.subtract(decodeOffset(state))
            if (level.getBlockState(anchorPos).block is BlueprintAnchorBlock) {
                level.destroyBlock(anchorPos, true)
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    /** 交互代理:右键成员格 = 右键锚点格(hitResult 保留玩家点击面,位置换锚点)。 */
    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult
    ): ItemInteractionResult {
        val anchorPos = pos.subtract(decodeOffset(state))
        val anchorBlock = level.getBlockState(anchorPos).block as? BlueprintAnchorBlock
        return if (anchorBlock != null) {
            anchorBlock.memberUseItemOn(stack, level.getBlockState(anchorPos), level, anchorPos, player, hand, hitResult)
        } else {
            super.useItemOn(stack, state, level, pos, player, hand, hitResult)
        }
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        val anchorPos = pos.subtract(decodeOffset(state))
        val anchorBlock = level.getBlockState(anchorPos).block as? BlueprintAnchorBlock
        return if (anchorBlock != null) {
            anchorBlock.memberUseWithoutItem(level.getBlockState(anchorPos), level, anchorPos, player, hitResult)
        } else {
            super.useWithoutItem(state, level, pos, player, hitResult)
        }
    }

    override fun codec(): MapCodec<out Block> = CODEC

    companion object {
        val OFFSET_X: IntegerProperty = IntegerProperty.create("offset_x", 0, 2)
        val OFFSET_Y: IntegerProperty = IntegerProperty.create("offset_y", 0, 2)
        val OFFSET_Z: IntegerProperty = IntegerProperty.create("offset_z", 0, 2)

        /** 0..2 存储偏置:存储值 - OFFSET_BIAS = 真偏移(-1..1)。 */
        private const val OFFSET_BIAS = 1

        private val CODEC: MapCodec<StructuralBlock> = simpleCodec(::StructuralBlock)

        /** 把偏移编进成员 blockstate(偏置存储);外观必须是 StructuralBlock(或其子类),否则破坏代理/能力路由失效。 */
        fun encodeOffset(base: BlockState, offset: BlockPos): BlockState {
            val block = base.block
            require(block is StructuralBlock) { "blueprint member appearance must be a StructuralBlock, got $block" }
            return base
                .setValue(OFFSET_X, offset.x + OFFSET_BIAS)
                .setValue(OFFSET_Y, offset.y + OFFSET_BIAS)
                .setValue(OFFSET_Z, offset.z + OFFSET_BIAS)
        }

        /** 解码成员格编码的锚点偏移(偏置存储 → 真偏移)。 */
        fun decodeOffset(state: BlockState): BlockPos = BlockPos(
            state.getValue(OFFSET_X) - OFFSET_BIAS,
            state.getValue(OFFSET_Y) - OFFSET_BIAS,
            state.getValue(OFFSET_Z) - OFFSET_BIAS
        )
    }
}