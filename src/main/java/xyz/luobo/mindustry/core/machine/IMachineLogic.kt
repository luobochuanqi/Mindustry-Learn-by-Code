package xyz.luobo.mindustry.core.machine

import net.minecraft.world.level.block.entity.BlockEntity

interface IMachineLogic {
    fun tick(be: BlockEntity)
    fun getTooltipData(be: BlockEntity): Map<String, Any>
    fun canProcess(be: BlockEntity): Boolean
    fun process(be: BlockEntity)
}