package xyz.luobo.mturrets.core.capability

import net.minecraft.world.level.material.Fluid
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler

/**
 * 液体 Capability 接口
 * 定义液体存储的基本功能
 */
interface IFluidCapability : IFluidHandler {

    /**
     * 液体容量
     */
    val fluidCapacity: Int

    /**
     * 最大输入速率（每 tick）
     */
    val maxReceive: Int

    /**
     * 最大输出速率（每 tick）
     */
    val maxExtract: Int

    /**
     * 当前存储的液体
     */
    var currentFluid: FluidStack

    /**
     * 液体变化回调
     */
    fun onFluidChanged()

    /**
     * 获取槽数量（默认 1）
     */
    override fun getTanks(): Int = 1

    /**
     * 获取指定槽的液体
     */
    override fun getFluidInTank(tank: Int): FluidStack {
        if (tank != 0) return FluidStack.EMPTY
        return currentFluid
    }

    /**
     * 获取指定槽的容量
     */
    override fun getTankCapacity(tank: Int): Int {
        if (tank != 0) return 0
        return fluidCapacity
    }

    /**
     * 检查是否可以填充液体
     */
    override fun isFluidValid(tank: Int, stack: FluidStack): Boolean {
        if (tank != 0) return false

        // 如果为空或液体类型相同，可以填充
        return currentFluid.isEmpty || currentFluid.fluid == stack.fluid
    }

    /**
     * 填充液体
     */
    override fun fill(resource: FluidStack, action: IFluidHandler.FluidAction): Int {
        if (resource.isEmpty) return 0
        if (!isFluidValid(0, resource)) return 0

        val spaceAvailable = fluidCapacity - currentFluid.amount
        val toFill = kotlin.math.min(maxReceive, kotlin.math.min(resource.amount, spaceAvailable))

        if (toFill <= 0) return 0

        if (action.execute()) {
            if (currentFluid.isEmpty) {
                currentFluid = FluidStack(resource.fluid, toFill)
            } else {
                currentFluid.grow(toFill)
            }
            onFluidChanged()
        }

        return toFill
    }

    /**
     * 提取液体
     */
    override fun drain(resource: FluidStack, action: IFluidHandler.FluidAction): FluidStack {
        if (resource.isEmpty) return FluidStack.EMPTY
        if (currentFluid.isEmpty || currentFluid.fluid != resource.fluid) return FluidStack.EMPTY

        val toDrain = kotlin.math.min(maxExtract, kotlin.math.min(resource.amount, currentFluid.amount))

        if (toDrain <= 0) return FluidStack.EMPTY

        if (action.execute()) {
            currentFluid.shrink(toDrain)
            if (currentFluid.isEmpty) {
                currentFluid = FluidStack.EMPTY
            }
            onFluidChanged()
        }

        return FluidStack(resource.fluid, toDrain)
    }

    /**
     * 提取液体（按数量）
     */
    override fun drain(maxDrain: Int, action: IFluidHandler.FluidAction): FluidStack {
        if (currentFluid.isEmpty) return FluidStack.EMPTY

        val toDrain = kotlin.math.min(maxExtract, kotlin.math.min(maxDrain, currentFluid.amount))

        if (toDrain <= 0) return FluidStack.EMPTY

        val result = FluidStack(currentFluid.fluid, toDrain)

        if (action.execute()) {
            currentFluid.shrink(toDrain)
            if (currentFluid.isEmpty) {
                currentFluid = FluidStack.EMPTY
            }
            onFluidChanged()
        }

        return result
    }

    /**
     * 获取液体存储百分比
     */
    fun getFluidPercentage(): Float {
        if (fluidCapacity <= 0) return 0f
        return currentFluid.amount.toFloat() / fluidCapacity.toFloat()
    }

    /**
     * 检查是否有足够的液体
     */
    fun hasFluid(amount: Int): Boolean = currentFluid.amount >= amount

    /**
     * 检查是否包含指定液体
     */
    fun containsFluid(fluid: Fluid): Boolean = currentFluid.fluid == fluid

    /**
     * 设置液体（直接设置，用于数据加载）
     */
    fun setFluid(fluid: FluidStack) {
        if (fluid.isEmpty) {
            currentFluid = FluidStack.EMPTY
        } else {
            currentFluid = FluidStack(fluid.fluid, fluid.amount.coerceIn(0, fluidCapacity))
        }
        onFluidChanged()
    }

    /**
     * 清空液体
     */
    fun clearFluid() {
        if (!currentFluid.isEmpty) {
            currentFluid = FluidStack.EMPTY
            onFluidChanged()
        }
    }
}