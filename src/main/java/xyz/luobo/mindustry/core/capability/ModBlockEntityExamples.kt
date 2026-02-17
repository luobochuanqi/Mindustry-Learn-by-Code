package xyz.luobo.mindustry.core.capability

import net.minecraft.core.BlockPos
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluids
import xyz.luobo.mindustry.core.MindustryModBlockEntity

/**
 * ModBlockEntity 使用示例
 * 展示如何继承 ModBlockEntity 并使用组合方式管理 Capability
 */

// ========== 示例 1：仅使用能量系统的 BE ==========

/**
 * 能量方块实体
 * 只有能量 Capability
 */
class EnergyOnlyBE(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : MindustryModBlockEntity(type, pos, state) {

    // 提供能量 Capability
    override val energyCapability = createEnergyCapability(
        capacity = 10000,
        maxReceive = 100,
        maxExtract = 50
    )

    // 不使用液体和物品
    override val fluidCapability = null
    override val itemCapability = null

    // 使用能量
    fun useEnergy(amount: Int): Boolean {
        val capability = energyCapability ?: return false
        return capability.hasEnergy(amount) && capability.extractEnergy(amount, false) > 0
    }

    // 添加能量
    fun addEnergy(amount: Int): Int {
        val capability = energyCapability ?: return 0
        return capability.receiveEnergy(amount, false)
    }
}

// ========== 示例 2：仅使用液体系统的 BE ==========

/**
 * 液体存储方块实体
 * 只有液体 Capability
 */
class FluidOnlyBE(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : MindustryModBlockEntity(type, pos, state) {

    // 提供液体 Capability
    override val fluidCapability = createFluidCapability(
        capacity = 8000,
        maxReceive = 100,
        maxExtract = 50
    )

    // 不使用能量和物品
    override val energyCapability = null
    override val itemCapability = null

    // 获取液体百分比
    fun getFluidPercentage(): Float {
        return fluidCapability?.getFluidPercentage() ?: 0f
    }
}

// ========== 示例 3：仅使用物品系统的 BE ==========

/**
 * 物品存储方块实体
 * 只有物品 Capability
 */
class ItemOnlyBE(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : MindustryModBlockEntity(type, pos, state) {

    // 提供物品 Capability（27 个槽位，类似箱子）
    override val itemCapability = createItemCapability(
        slotCount = 27
    )

    // 不使用能量和液体
    override val energyCapability = null
    override val fluidCapability = null

    // 获取物品总数
    fun getTotalItems(): Int {
        val capability = itemCapability ?: return 0
        var total = 0
        for (i in 0 until capability.slotCount) {
            total += capability.getStack(i).count
        }
        return total
    }
}

// ========== 示例 4：能量 + 液体系统 ==========

/**
 * 能量液体方块实体
 * 同时使用能量和液体 Capability
 */
class EnergyFluidBE(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : MindustryModBlockEntity(type, pos, state) {

    // 提供能量 Capability
    override val energyCapability = createEnergyCapability(
        capacity = 5000,
        maxReceive = 50,
        maxExtract = 0  // 不能输出，只能输入
    )

    // 提供液体 Capability
    override val fluidCapability = createFluidCapability(
        capacity = 4000,
        maxReceive = 50,
        maxExtract = 0  // 不能输出，只能输入
    )

    // 不使用物品
    override val itemCapability = null

    // 液体变化时消耗能量
    override fun onFluidChanged() {
        super.onFluidChanged()
        // 消耗能量处理液体
        val energyNeeded = 10
        if (energyCapability?.hasEnergy(energyNeeded) == true) {
            energyCapability?.extractEnergy(energyNeeded, false)
        }
    }
}

// ========== 示例 5：能量 + 物品系统（机器）==========

/**
 * 机器方块实体
 * 使用能量和物品 Capability
 */
class MachineBE(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : MindustryModBlockEntity(type, pos, state) {

    // 提供能量 Capability
    override val energyCapability = createEnergyCapability(
        capacity = 10000,
        maxReceive = 100,
        maxExtract = 0  // 机器通常不输出能量
    )

    // 提供物品 Capability（2 个输入槽，1 个输出槽）
    override val itemCapability = createItemCapability(
        slotCount = 3,
        canInsert = { slot -> slot < 2 },  // 前 2 个槽为输入
        canExtract = { slot -> slot == 2 }  // 第 3 个槽为输出
    )

    // 不使用液体
    override val fluidCapability = null

    // 覆盖槽位检查方法
    override fun isInputSlot(slot: Int) = slot < 2
    override fun isOutputSlot(slot: Int) = slot == 2

    // 工作逻辑
    fun tick() {
        val capability = energyCapability ?: return
        val itemCapability = itemCapability ?: return

        // 检查是否有能量
        if (!capability.hasEnergy(5)) return

        // 检查输入槽是否有物品
        val input1 = itemCapability.getStack(0)
        val input2 = itemCapability.getStack(1)

        if (input1.isEmpty || input2.isEmpty) return

        // 消耗能量
        capability.extractEnergy(5, false)

        // 消耗输入物品
        input1.shrink(1)
        input2.shrink(1)

        // 生成输出物品
        val output = itemCapability.getStack(2)
        if (output.isEmpty) {
            itemCapability.setStack(2, Items.DIAMOND.defaultInstance)
        } else {
            output.grow(1)
        }
    }
}

// ========== 示例 6：能量 + 液体 + 物品系统（高级机器）==========

/**
 * 高级机器方块实体
 * 同时使用所有三个 Capability
 */
class AdvancedMachineBE(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : MindustryModBlockEntity(type, pos, state) {

    // 提供能量 Capability
    override val energyCapability = createEnergyCapability(
        capacity = 20000,
        maxReceive = 200,
        maxExtract = 0
    )

    // 提供液体 Capability
    override val fluidCapability = createFluidCapability(
        capacity = 10000,
        maxReceive = 100,
        maxExtract = 50  // 可以输出液体
    )

    // 提供物品 Capability（4 个槽位）
    override val itemCapability = createItemCapability(
        slotCount = 4,
        canInsert = { slot -> slot < 3 },
        canExtract = { slot -> slot == 3 }
    )

    // 覆盖槽位检查方法
    override fun isInputSlot(slot: Int) = slot < 3
    override fun isOutputSlot(slot: Int) = slot == 3

    // 工作逻辑
    fun tick() {
        val energyCap = energyCapability ?: return
        val fluidCap = fluidCapability ?: return
        val itemCap = itemCapability ?: return

        // 检查资源
        if (!energyCap.hasEnergy(10)) return
        if (!fluidCap.containsFluid(Fluids.WATER)) return
        if (!fluidCap.hasFluid(10)) return

        // 检查输入物品
        val input = itemCap.getStack(0)
        if (input.isEmpty) return

        // 消耗资源
        energyCap.extractEnergy(10, false)
        fluidCap.drain(10, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)
        input.shrink(1)

        // 生成输出
        val output = itemCap.getStack(3)
        if (output.isEmpty) {
            itemCap.setStack(3, Items.GOLD_INGOT.defaultInstance)
        } else {
            output.grow(1)
        }

        // 输出液体
        fluidCap.fill(
            net.neoforged.neoforge.fluids.FluidStack(Fluids.LAVA, 5),
            net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE
        )
    }
}

/**
 * ModBlockEntity 使用指南
 *
 * 1. 继承 ModBlockEntity：
 *    class MyBE(type, pos, state) : ModBlockEntity(type, pos, state)
 *
 * 2. 选择需要的 Capability：
 *    - 能量：override energyCapability
 *    - 液体：override fluidCapability
 *    - 物品：override itemCapability
 *
 * 3. 创建 Capability：
 *    - createEnergyCapability(capacity, maxReceive, maxExtract)
 *    - createFluidCapability(capacity, maxReceive, maxExtract)
 *    - createItemCapability(slotCount, canInsert, canExtract)
 *
 * 4. 访问 Capability：
 *    - energyCapability?.receiveEnergy(amount, false)
 *    - fluidCapability?.fill(fluid, action)
 *    - itemCapability?.insertItem(slot, stack, false)
 *
 * 5. 覆盖回调（可选）：
 *    - onEnergyChanged() - 能量变化时调用
 *    - onFluidChanged() - 液体变化时调用
 *    - onContentsChanged(slot) - 物品变化时调用
 *    - isInputSlot(slot) - 检查输入槽
 *    - isOutputSlot(slot) - 检查输出槽
 *
 * 6. 检查支持：
 *    - hasEnergySupport() - 是否支持能量
 *    - hasFluidSupport() - 是否支持液体
 *    - hasItemSupport() - 是否支持物品
 *
 * 7. 自动保存：
 *    - Capability 数据会自动保存和加载
 *    - 不需要手动处理 NBT
 */