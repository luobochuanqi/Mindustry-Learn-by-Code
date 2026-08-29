package xyz.luobo.mturrets.common.structure

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mturrets.common.ModBlocks
import xyz.luobo.mturrets.core.structure.Blueprint
import xyz.luobo.mturrets.core.structure.BlueprintAnchorBlock

// 骨架临时测试方块(#32 验收用):真内容 #33(窑炉 1×1)/#34(Scatter 2×2)落地后删除。

/** 测试 2×2 结构锚点:角锚点 +X/+Z 生长(#26 约定),成员为 3 格。 */
class TestStructureAnchor2x2Block : BlueprintAnchorBlock(structureProperties()) {
    override val blueprint: Blueprint = Blueprint(
        listOf(
            Blueprint.MemberSpec(BlockPos(1, 0, 0)) { ModBlocks.TEST_STRUCTURAL.get().defaultBlockState() },
            Blueprint.MemberSpec(BlockPos(0, 0, 1)) { ModBlocks.TEST_STRUCTURAL.get().defaultBlockState() },
            Blueprint.MemberSpec(BlockPos(1, 0, 1)) { ModBlocks.TEST_STRUCTURAL.get().defaultBlockState() }
        )
    )

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = TestStructureAnchorBE(pos, state)

    override fun codec(): MapCodec<out BaseEntityBlock?> = CODEC

    companion object {
        private val CODEC: MapCodec<TestStructureAnchor2x2Block> = simpleCodec { TestStructureAnchor2x2Block() }
    }
}
