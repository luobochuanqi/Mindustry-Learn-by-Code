package xyz.luobo.mindustry.common.machines.graphite_press

import com.mojang.serialization.MapCodec
import net.minecraft.world.level.block.BaseEntityBlock
import xyz.luobo.mindustry.core.multiblock.MultiblockController

class GraphitePressBlock : MultiblockController(Properties.of().strength(3.0f)) {
    override val blockDefinitionId = GraphitePress.ID
    override fun codec(): MapCodec<out BaseEntityBlock?> {
        TODO("Not yet implemented")
    }
}