package xyz.luobo.mturrets.core.structure

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.Containers
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.PushReaction

/**
 * 蓝图管线锚点基类(ADR-0003):玩家放置后下一 tick 按蓝图盖成员格;
 * 校验失败整体回滚(退控制器物品 + actionbar);任何移除路径同步拆解成员。
 * 放置/拆除收口在本类,逻辑与状态只住锚点 BE(见 [BlueprintAnchor])。
 */
abstract class BlueprintAnchorBlock(properties: Properties) : BaseEntityBlock(properties) {
    /** 本锚点方块的固定形状;拆除收口不依赖运行时数据,形状必须静态可知。 */
    abstract val blueprint: Blueprint

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    /**
     * 排程成型 tick。oldState 与新状态同方块时跳过:区块重载/重复 setBlock 会重放
     * onPlace,重放时不能二次成型(Create 水车同款守卫)。
     */
    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, isMoving: Boolean) {
        super.onPlace(state, level, pos, oldState, isMoving)
        if (oldState.block == state.block) return
        if (!level.isClientSide) level.scheduleTick(pos, state.block, 1)
    }

    /**
     * 成型 tick:先全量校验成员目标格可替换,再统一盖格——失败不留半截结构。
     * 延迟一 tick 是刻意的:onPlace 内 setBlock 会重入(LevelChunk.setBlockState 先提交后回调)。
     */
    override fun tick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
        val blueprint = (state.block as BlueprintAnchorBlock).blueprint
        val cells = blueprint.members.map { spec ->
            pos.offset(spec.offset) to StructuralBlock.encodeOffset(spec.stateProvider(), spec.offset)
        }
        val blockedCell = cells.firstOrNull { (cellPos, _) -> !level.getBlockState(cellPos).canBeReplaced() }
        if (blockedCell != null) {
            rollbackPlacement(level, pos, state)
            return
        }
        for ((cellPos, memberState) in cells) {
            level.setBlockAndUpdate(cellPos, memberState)
        }
    }

    /**
     * 成型失败回滚:锚点无痕消失(不走 loot);非创造在锚点位退还控制器物品;
     * 就近玩家 actionbar 提示。无 ghost 预览(ADR-0008)。
     */
    private fun rollbackPlacement(level: ServerLevel, pos: BlockPos, state: BlockState) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)
        val player = level.getNearestPlayer(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, 16.0) { !it.isSpectator }
        if (player != null) {
            player.displayClientMessage(Component.translatable("mturrets.message.blueprint_blocked"), true)
        }
        if (player == null || !player.isCreative) {
            Containers.dropItemStack(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, ItemStack(state.block.asItem()))
        }
    }

    /**
     * 拆除收口:任何路径去掉锚点 → 同步清掉全部成员格 + 散落内容物。
     * 成员 loot 为空表,掉落只经锚点发生;成员被清时其破坏代理的守卫
     * (锚点格已为空气)自然短路,不会递归。
     */
    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!level.isClientSide && newState.block != state.block) {
            val blueprint = (state.block as BlueprintAnchorBlock).blueprint
            // BE 在 super.onRemove 才移除,此刻仍可读,内容物随拆除散落
            val be = level.getBlockEntity(pos)
            if (be is BlueprintAnchor) {
                for (stack in be.contentsToScatter(false)) {
                    Containers.dropItemStack(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, stack)
                }
            }
            for (spec in blueprint.members) {
                val memberPos = pos.offset(spec.offset)
                val memberState = level.getBlockState(memberPos)
                // 归属校验:只拆"成员编码指向本锚点"的格。放置回滚/相邻贴放时,
                // 本蓝图偏移上可能是相邻结构的成员格(同类型方块),误清会触发对方
                // 破坏代理、把好端端的邻居整机拆掉。
                if (memberState.block is StructuralBlock
                    && memberPos.subtract(StructuralBlock.decodeOffset(memberState)) == pos
                ) {
                    level.removeBlock(memberPos, false)
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    companion object {
        /** 锚点与成员共同的 block properties:拒绝活塞/水流推动,结构完整性不受环境影响。 */
        fun structureProperties(): Properties = Properties.of().pushReaction(PushReaction.BLOCK)
    }
}