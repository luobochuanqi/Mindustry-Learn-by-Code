package xyz.luobo.mindustry.core.capability.impl

import net.neoforged.neoforge.fluids.FluidStack
import xyz.luobo.mindustry.core.capability.IFluidCapability

/**
 * 液体 Capability 默认实现
 */
class FluidCapabilityImpl(
    override val fluidCapacity: Int,
    override val maxReceive: Int = 0,
    override val maxExtract: Int = 0,
    private val onFluidChangedCallback: () -> Unit = {}
) : IFluidCapability {

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
        return FluidCapabilityImpl(fluidCapacity, maxReceive, maxExtract, onFluidChangedCallback).apply {
            currentFluid = this@FluidCapabilityImpl.currentFluid.copy()
        }
    }
}