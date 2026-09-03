package xyz.luobo.mturrets.core.structure

import com.mojang.serialization.MapCodec
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState

/**
 * 炮台成员格(#42):全模型资产架构下方块侧渲染为空,几何由锚点 BE visual 承担;
 * blockstate 保留 elementless 的 particle 模型供敲击/破坏粒子取色。
 * 钻头等成员格仍走 [StructuralBlock] 的逐格外观(资产迁移不在本票)。
 */
class TurretStructuralBlock(properties: Properties) : StructuralBlock(properties) {
    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.INVISIBLE

    override fun codec(): MapCodec<out Block> = CODEC

    companion object {
        private val CODEC: MapCodec<TurretStructuralBlock> = simpleCodec(::TurretStructuralBlock)
    }
}
