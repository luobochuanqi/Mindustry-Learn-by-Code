package xyz.luobo.mturrets.core.structure

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.IntegerProperty

/**
 * 成员格(ADR-0003):无 BE、无 tick、无交互;碰撞/选中整格;破坏代理回锚点。
 *
 * 相对锚点的偏移编进 blockstate(0..2,偏置 1 表示 -1..1,与 [Blueprint] 跨距天花板一致;
 * vanilla 的 IntegerProperty 不允许负最小值),锚点坐标 = 本格坐标 - 解码偏移,
 * 成员格零持久化引用。
 */
class StructuralBlock(properties: Properties) : Block(properties) {
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