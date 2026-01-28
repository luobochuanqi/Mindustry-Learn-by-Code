package xyz.luobo.mindustry.core.machine

import net.minecraft.world.level.block.entity.BlockEntity

/**
 * 用于规范机器的逻辑函数.
 */
@Deprecated("暂时未启用")
interface IMachineLogic {
    fun serverTick(be: BlockEntity)
    fun clientTick(be: BlockEntity)
}