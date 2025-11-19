package xyz.luobo.mindustry.common.machines.graphite_press

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mindustry.common.ModBlockEntities
import xyz.luobo.mindustry.core.multiblock.MultiblockBlockEntity

class GraphitePressBlockEntity(pos: BlockPos, state: BlockState) :
    MultiblockBlockEntity(ModBlockEntities.GRAPHITE_PRESS_CONTROLLER.get(), pos, state) {

    var progress = 0
    val maxProgress = 100

    // 从 MachineDefinition 获取配置
    val config = MachineRegistry.get(GraphitePress.ID)!!

//    override fun tick() {
//        config.logic.tick(this)
//    }

    fun hasInput(): Boolean {
        TODO("实现能源库存逻辑")
    }
}