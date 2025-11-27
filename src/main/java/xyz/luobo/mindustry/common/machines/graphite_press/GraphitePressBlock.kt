package xyz.luobo.mindustry.common.machines.graphite_press

import com.mojang.serialization.MapCodec
import net.minecraft.world.level.block.BaseEntityBlock
import xyz.luobo.mindustry.core.multiblock.MultiblockControllerBlock

class GraphitePressBlock :
    MultiblockControllerBlock(Properties.of().strength(3.0f)) {
    
    override fun codec(): MapCodec<out BaseEntityBlock?> {
        TODO("Not yet implemented")
    }
}