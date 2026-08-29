package xyz.luobo.mturrets.core.capability.impl

import net.neoforged.neoforge.fluids.FluidStack
import xyz.luobo.mturrets.core.capability.IFluidCapability
/**
 * 液体 Capability 默认实现
 */
class FluidCapabilityImpl(
    override val fluidCapacity: Int,
    override val maxReceive: Int = 0,
    override val maxExtract: Int = 0,
    private val onFluidChangedCallback: () -> Unit = {},
    private val isValidFluidCallback: (FluidStack) -> Boolean = { true }
) : IFluidCapability {

    /** 本罐可容纳的液体(空罐或同种液可继续填);过滤器先决。 */
    override fun isFluidValid(tank: Int, stack: FluidStack): Boolean {
        return isValidFluidCallback(stack) && super<IFluidCapability>.isFluidValid(tank, stack)
    }

    override var currentFluid: FluidStack = FluidStack.EMPTY
        set(value) {
            field = if (value.isEmpty) FluidStack.EMPTY else {
                FluidStack(value.fluid, value.amount.coerceIn(0, fluidCapacity))
            }
        }

    override fun onFluidChanged() {
        onFluidChangedCallback()
    }

    /**
     * 复制当前状态
     */
    fun copy(): FluidCapabilityImpl {
        return FluidCapabilityImpl(fluidCapacity, maxReceive, maxExtract, onFluidChangedCallback, isValidFluidCallback).apply {
            currentFluid = this@FluidCapabilityImpl.currentFluid.copy()
        }
    }
}