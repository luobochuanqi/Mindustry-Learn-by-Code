package xyz.luobo.mindustry.core.machine

import net.minecraft.resources.ResourceLocation
import xyz.luobo.mindustry.core.registry.MachineDefinition

/**
 * 用于定义该机器的基础参数, 用于简化机器注册流程.
 * 只需调用该文件 register 即可同步注册 Controller, Dummy, BlockItem, BlockEntity.
 */
interface IMachine {
    fun getMachineDefinition(): MachineDefinition
    fun getMachineID(): ResourceLocation
    fun registerMachine()
}