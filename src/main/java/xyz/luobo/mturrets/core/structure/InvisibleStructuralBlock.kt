package xyz.luobo.mturrets.core.structure

import com.mojang.serialization.MapCodec
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState

/**
 * 全模型资产架构成员格(ADR-0005 #42 / ADR-0011):方块侧渲染为空,几何由锚点 BE visual 承担;
 * blockstate 保留 elementless 的 particle 模型供敲击/破坏粒子取色。
 * 炮台(Scatter)与钻头(Drill)成员格共用此空渲染结构块。
 */
class InvisibleStructuralBlock(properties: Properties) : StructuralBlock(properties) {
    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.INVISIBLE

    override fun codec(): MapCodec<out Block> = CODEC

    companion object {
        private val CODEC: MapCodec<InvisibleStructuralBlock> = simpleCodec(::InvisibleStructuralBlock)
    }
}
