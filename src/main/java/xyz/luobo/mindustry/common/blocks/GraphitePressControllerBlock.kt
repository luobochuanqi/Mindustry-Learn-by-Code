package xyz.luobo.mindustry.common.blocks

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.DirectionProperty
import xyz.luobo.mindustry.common.ModBlocks
import xyz.luobo.mindustry.common.blockEntities.GraphitePressController

class GraphitePressControllerBlock : BaseEntityBlock(Properties.of()
    .strength(2.0f)
    .requiresCorrectToolForDrops()
) {
    companion object {
        @JvmStatic
        val CODEC: MapCodec<GraphitePressControllerBlock> = simpleCodec(
            { GraphitePressControllerBlock() }
        )

        val FACING: DirectionProperty = BlockStateProperties.HORIZONTAL_FACING
    }

    init {
        // 设置默认状态（朝北）
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH))
    }

    override fun newBlockEntity(
        pos: BlockPos,
        state: BlockState
    ): BlockEntity? {
        return GraphitePressController(pos = pos, state = state)
    }

    override fun <T : BlockEntity?> getTicker(
        level: Level,
        state: BlockState,
        blockEntityType: BlockEntityType<T?>
    ): BlockEntityTicker<T?>? {
        return super.getTicker(level, state, blockEntityType)
    }

    override fun codec(): MapCodec<out BaseEntityBlock?> {
        return CODEC
    }

    // 创建方块状态定义
    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    // 获取放置时的朝向（基于玩家视角）
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        return defaultBlockState().setValue(
            FACING,
            context.horizontalDirection.opposite
        )
    }

    // 当方块被放置时的处理
    override fun setPlacedBy(
        level: Level,
        pos: BlockPos,
        state: BlockState,
        placer: LivingEntity?,
        stack: ItemStack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack)

        if (level.isClientSide) return

        val facing = state.getValue(FACING)
        createMultiblockStructure(level, pos, facing)
    }

    // 创建2^3多方块结构
    private fun createMultiblockStructure(level: Level, controllerPos: BlockPos, facing: Direction) {
        // 根据朝向计算其他三个方块的位置
        val positions = calculateStructurePositions(controllerPos, facing)

        // 检查所有位置是否都可以放置方块
        if (!canPlaceStructure(level, positions)) {
            // 如果不能放置，破坏控制器方块
            level.destroyBlock(controllerPos, true)
            return
        }

        // 放置其他三个结构方块
        for ((index, pos) in positions.withIndex()) {
            // 跳过控制器自身的位置（索引0）
            if (index == 0) continue

            level.setBlock(pos, ModBlocks.MULTI_BLOCK_DUMMY_BLOCK.get().defaultBlockState(), 3)
        }
    }

    // 计算2^3的四个位置
    private fun calculateStructurePositions(controllerPos: BlockPos, facing: Direction): List<BlockPos> {
        return when (facing) {
            Direction.NORTH -> listOf(
                controllerPos,                          // 控制器位置 (0,0)
                controllerPos.east(),                   // 右侧 (1,0)
                controllerPos.south(),                  // 前方 (0,1)
                controllerPos.east().south()           // 右前方 (1,1)
            )
            Direction.SOUTH -> listOf(
                controllerPos,                          // 控制器位置 (0,0)
                controllerPos.west(),                   // 右侧 (1,0)
                controllerPos.north(),                  // 前方 (0,1)
                controllerPos.west().north()           // 右前方 (1,1)
            )
            Direction.EAST -> listOf(
                controllerPos,                          // 控制器位置 (0,0)
                controllerPos.south(),                  // 右侧 (1,0)
                controllerPos.west(),                   // 前方 (0,1)
                controllerPos.south().west()           // 右前方 (1,1)
            )
            Direction.WEST -> listOf(
                controllerPos,                          // 控制器位置 (0,0)
                controllerPos.north(),                  // 右侧 (1,0)
                controllerPos.east(),                   // 前方 (0,1)
                controllerPos.north().east()           // 右前方 (1,1)
            )
            else -> listOf(controllerPos) // 默认情况
        }
    }

    // 检查是否可以放置结构
    private fun canPlaceStructure(level: Level, positions: List<BlockPos>): Boolean {
        for ((index, pos) in positions.withIndex()) {
            // 跳过控制器位置（索引0），因为它已经被放置
            if (index == 0) continue

            val state = level.getBlockState(pos)
            // 检查位置是否可替换（空气、草、花等）
            if (!state.canBeReplaced()) {
                return false
            }
        }
        return true
    }

    // 当方块被破坏时的处理
    override fun onRemove(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        newState: BlockState,
        movedByPiston: Boolean
    ) {
        // 破坏控制器时同时破坏其他结构方块
        if (!level.isClientSide) {
            val facing = state.getValue(FACING)
            val positions = calculateStructurePositions(pos, facing)

            for ((index, structurePos) in positions.withIndex()) {
                // 跳过控制器自身
                if (index == 0) continue

                // 如果位置是结构方块，则破坏它
                if (level.getBlockState(structurePos).`is`(ModBlocks.MULTI_BLOCK_DUMMY_BLOCK.get())) {
                    level.destroyBlock(structurePos, false)
                }
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, movedByPiston: Boolean) {
        super.onPlace(state, level, pos, oldState, movedByPiston)
    }

    override fun neighborChanged(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        neighborBlock: Block,
        neighborPos: BlockPos,
        movedByPiston: Boolean
    ) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston)

        // 可以在这里添加结构完整性检查
        if (!level.isClientSide) {
            checkStructureIntegrity(level, pos, state)
        }
    }

    // 检查结构完整性
    private fun checkStructureIntegrity(level: Level, controllerPos: BlockPos, state: BlockState) {
        val facing = state.getValue(FACING)
        val positions = calculateStructurePositions(controllerPos, facing)

        for ((index, pos) in positions.withIndex()) {
            if (index == 0) continue // 跳过控制器

            val blockState = level.getBlockState(pos)
            if (!blockState.`is`(ModBlocks.MULTI_BLOCK_DUMMY_BLOCK.get())) {
                // 结构不完整，破坏整个结构
                level.destroyBlock(controllerPos, true)
                break
            }
        }
    }

    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.MODEL
    }
}