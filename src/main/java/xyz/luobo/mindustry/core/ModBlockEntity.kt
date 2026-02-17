package xyz.luobo.mindustry.core

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.fluids.FluidStack
import xyz.luobo.mindustry.core.capability.IEnergyCapability
import xyz.luobo.mindustry.core.capability.IFluidCapability
import xyz.luobo.mindustry.core.capability.IItemCapability
import xyz.luobo.mindustry.core.capability.impl.EnergyCapabilityImpl
import xyz.luobo.mindustry.core.capability.impl.FluidCapabilityImpl
import xyz.luobo.mindustry.core.capability.impl.ItemCapabilityImpl

/**
 * Mod 方块实体基类
 * 使用组合方式管理 Capability（能量、液体、物品）
 *
 * 子类可以根据需要选择启用哪些 Capability：
 * - 能量系统：通过 energyCapability 属性访问
 * - 液体系统：通过 fluidCapability 属性访问
 * - 物品系统：通过 itemCapability 属性访问
 */
abstract class ModBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : BlockEntity(type, pos, state) {

    // ========== Capability 管理 ==========

    /**
     * 能量 Capability（可选）
     * 子类通过 override energyCapability 提供实现
     */
    open val energyCapability: IEnergyCapability? = null

    /**
     * 液体 Capability（可选）
     * 子类通过 override fluidCapability 提供实现
     */
    open val fluidCapability: IFluidCapability? = null

    /**
     * 物品 Capability（可选）
     * 子类通过 override itemCapability 提供实现
     */
    open val itemCapability: IItemCapability? = null

    // ========== 便捷访问方法 ==========

    /**
     * 检查是否支持能量
     */
    fun hasEnergySupport(): Boolean = energyCapability != null

    /**
     * 检查是否支持液体
     */
    fun hasFluidSupport(): Boolean = fluidCapability != null

    /**
     * 检查是否支持物品
     */
    fun hasItemSupport(): Boolean = itemCapability != null

    /**
     * 同步数据到客户端
     * 子类可以重写此方法添加自定义同步逻辑
     */
    open fun syncData() {
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
        setChanged()
    }

    // ========== 数据保存 ==========

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)

        // 保存能量数据
        energyCapability?.let { capability ->
            tag.putInt("energy", capability.currentEnergy)
        }

        // 保存液体数据
        fluidCapability?.let { capability ->
            if (!capability.currentFluid.isEmpty) {
                tag.put("fluid", capability.currentFluid.save(registries))
            }
        }

        // 保存物品数据
        itemCapability?.let { capability ->
            val itemsTag = CompoundTag()
            for (i in 0 until capability.slotCount) {
                val stack = capability.getStack(i)
                if (!stack.isEmpty) {
                    itemsTag.put("slot_$i", stack.save(registries))
                }
            }
            if (itemsTag.size() > 0) {
                tag.put("items", itemsTag)
            }
        }
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)

        // 加载能量数据
        energyCapability?.let { capability ->
            capability.setEnergy(tag.getInt("energy"))
        }

        // 加载液体数据
        fluidCapability?.let { capability ->
            if (tag.contains("fluid")) {
                capability.setFluid(FluidStack.parse(registries, tag.getCompound("fluid")).orElse(FluidStack.EMPTY))
            }
        }

        // 加载物品数据
        itemCapability?.let { capability ->
            if (tag.contains("items")) {
                val itemsTag = tag.getCompound("items")
                for (i in 0 until capability.slotCount) {
                    val key = "slot_$i"
                    if (itemsTag.contains(key)) {
                        capability.setStack(
                            i,
                            ItemStack.parse(registries, itemsTag.getCompound(key)).orElse(ItemStack.EMPTY)
                        )
                    }
                }
            }
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 创建能量 Capability
     */
    protected fun createEnergyCapability(
        capacity: Int,
        maxReceive: Int = 0,
        maxExtract: Int = 0
    ): EnergyCapabilityImpl {
        return EnergyCapabilityImpl(
            energyCapacity = capacity,
            maxReceive = maxReceive,
            maxExtract = maxExtract
        ) { onEnergyChanged() }
    }

    /**
     * 创建液体 Capability
     */
    protected fun createFluidCapability(
        capacity: Int,
        maxReceive: Int = 0,
        maxExtract: Int = 0
    ): FluidCapabilityImpl {
        return FluidCapabilityImpl(
            fluidCapacity = capacity,
            maxReceive = maxReceive,
            maxExtract = maxExtract
        ) { onFluidChanged() }
    }

    /**
     * 创建物品 Capability
     */
    protected fun createItemCapability(
        slotCount: Int,
        canInsert: (slot: Int) -> Boolean = { true },
        canExtract: (slot: Int) -> Boolean = { true }
    ): ItemCapabilityImpl {
        return ItemCapabilityImpl(
            slotCount = slotCount,
            onContentsChangedCallback = { slot -> onContentsChanged(slot) },
            canInsertCallback = canInsert,
            canExtractCallback = canExtract
        )
    }

    /**
     * 能量变化回调（子类可重写）
     */
    protected open fun onEnergyChanged() {
        setChanged()
        syncData()
    }

    /**
     * 液体变化回调（子类可重写）
     */
    protected open fun onFluidChanged() {
        setChanged()
        syncData()
    }

    /**
     * 物品变化回调（子类可重写）
     */
    protected open fun onContentsChanged(slot: Int) {
        setChanged()
        syncData()
    }

    /**
     * 检查槽位是否为输出槽（子类可重写）
     */
    protected open fun isOutputSlot(slot: Int): Boolean = true

    /**
     * 检查槽位是否为输入槽（子类可重写）
     */
    protected open fun isInputSlot(slot: Int): Boolean = true
}