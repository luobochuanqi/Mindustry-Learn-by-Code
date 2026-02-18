package xyz.luobo.mindustry.core.capability.impl

import net.minecraft.world.item.ItemStack
import xyz.luobo.mindustry.core.capability.IItemCapability

/**
 * 物品 Capability 默认实现
 */
class ItemCapabilityImpl(
    override val slotCount: Int,
    private val onContentsChangedCallback: (slot: Int) -> Unit = {},
    private val canInsertCallback: (slot: Int) -> Boolean = { true },
    private val canExtractCallback: (slot: Int) -> Boolean = { true },
    private val isValidItemCallback: (slot: Int, ItemStack) -> Boolean = { _, _ -> true }
) : IItemCapability {

    private val stacks: Array<ItemStack> = Array(slotCount) { ItemStack.EMPTY }

    override fun getStack(slot: Int): ItemStack {
        if (slot !in 0..<slotCount) return ItemStack.EMPTY
        return stacks[slot]
    }

    override fun setStack(slot: Int, stack: ItemStack) {
        if (slot !in 0..<slotCount) return
        stacks[slot] = stack
    }

    override fun onContentsChanged(slot: Int) {
        onContentsChangedCallback(slot)
    }

    override fun canInsert(slot: Int): Boolean {
        if (slot !in 0..<slotCount) return false
        return canInsertCallback(slot)
    }

    override fun canExtract(slot: Int): Boolean {
        if (slot !in 0..<slotCount) return false
        return canExtractCallback(slot)
    }

    override fun isValidItemForSlot(slot: Int, stack: ItemStack): Boolean {
        if (slot !in 0..<slotCount) return false
        if (stack.isEmpty) return false
        return isValidItemCallback(slot, stack)
    }

    /**
     * 复制当前状态
     */
    fun copy(): ItemCapabilityImpl {
        val copy = ItemCapabilityImpl(
            slotCount,
            onContentsChangedCallback,
            canInsertCallback,
            canExtractCallback,
            isValidItemCallback
        )
        for (i in 0 until slotCount) {
            copy.setStack(i, stacks[i].copy())
        }
        return copy
    }
}