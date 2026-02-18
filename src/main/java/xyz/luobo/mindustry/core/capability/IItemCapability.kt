package xyz.luobo.mindustry.core.capability

import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.IItemHandler

/**
 * 物品 Capability 接口
 * 定义物品存储的基本功能
 */
interface IItemCapability : IItemHandler {

    /**
     * 槽位数量
     */
    val slotCount: Int

    /**
     * 物品变化回调
     */
    fun onContentsChanged(slot: Int)

    /**
     * 获取槽位数量
     */
    override fun getSlots(): Int = slotCount

    /**
     * 获取指定槽位的物品
     */
    override fun getStackInSlot(slot: Int): ItemStack {
        if (slot !in 0..<slotCount) return ItemStack.EMPTY
        return getStack(slot)
    }

    /**
     * 获取指定槽位的物品（子类实现）
     */
    fun getStack(slot: Int): ItemStack

    /**
     * 向指定槽位插入物品
     */
    override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
        if (stack.isEmpty) return ItemStack.EMPTY
        if (slot !in 0..<slotCount) return stack

        if (!canInsert(slot)) return stack

        val existingStack = getStack(slot)

        if (!existingStack.isEmpty) {
            // 检查是否可以堆叠
            if (!ItemStack.isSameItemSameComponents(existingStack, stack)) return stack

            val spaceAvailable = existingStack.maxStackSize - existingStack.count
            val toInsert = kotlin.math.min(stack.count, spaceAvailable)

            if (toInsert <= 0) return stack

            if (!simulate) {
                existingStack.grow(toInsert)
                onContentsChanged(slot)
            }

            return stack.copyWithCount(stack.count - toInsert)
        } else {
            // 槽位为空，直接插入
            val toInsert = kotlin.math.min(stack.count, stack.maxStackSize)

            if (!simulate) {
                setStack(slot, stack.copyWithCount(toInsert))
                onContentsChanged(slot)
            }

            return stack.copyWithCount(stack.count - toInsert)
        }
    }

    /**
     * 从指定槽位提取物品
     */
    override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack {
        if (amount <= 0) return ItemStack.EMPTY
        if (slot !in 0..<slotCount) return ItemStack.EMPTY

        if (!canExtract(slot)) return ItemStack.EMPTY

        val existingStack = getStack(slot)
        if (existingStack.isEmpty) return ItemStack.EMPTY

        val toExtract = kotlin.math.min(amount, existingStack.count)

        if (simulate) {
            return existingStack.copyWithCount(toExtract)
        }

        val extracted = existingStack.copyWithCount(toExtract)
        existingStack.shrink(toExtract)

        if (existingStack.isEmpty) {
            setStack(slot, ItemStack.EMPTY)
        }

        onContentsChanged(slot)
        return extracted
    }

    /**
     * 获取槽位限制
     */
    override fun getSlotLimit(slot: Int): Int {
        if (slot !in 0..<slotCount) return 0
        return getStack(slot).maxStackSize
    }

    /**
     * 检查物品是否有效
     * 
     * 根据 NeoForge 规范：
     * - 返回 false 表示这个槽位永远不能插入这种物品
     * - 返回 true 表示可能在某些情况下可以插入（需要进一步模拟）
     * - 不考虑当前的库存状态、满度或其他状态
     * 
     * @param slot 要查询的槽位
     * @param stack 要测试的物品栈
     * @return true 如果槽位可以在某些情况下插入该物品，false 如果永远不能插入
     */
    override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
        if (slot !in 0..<slotCount) return false
        if (stack.isEmpty) return false
        if (!canInsert(slot)) return false

        // 调用子类的物品验证方法
        return isValidItemForSlot(slot, stack)
    }

    /**
     * 检查物品是否可以插入到指定槽位（不考虑当前库存状态）
     * 子类可以重写此方法来限制可以插入的物品类型
     * 
     * 默认实现：所有物品都可以插入
     * 
     * @param slot 槽位索引
     * @param stack 要插入的物品栈
     * @return true 如果该物品类型可以被插入到该槽位
     */
    fun isValidItemForSlot(slot: Int, stack: ItemStack): Boolean = true

    /**
     * 检查是否可以插入（子类可重写）
     */
    fun canInsert(slot: Int): Boolean = true

    /**
     * 检查是否可以提取（子类可重写）
     */
    fun canExtract(slot: Int): Boolean = true

    /**
     * 设置指定槽位的物品（子类实现）
     */
    fun setStack(slot: Int, stack: ItemStack)

    /**
     * 获取指定槽位的容量
     */
    fun getSlotCapacity(slot: Int): Int = getSlotLimit(slot)

    /**
     * 获取指定槽位的剩余空间
     */
    fun getRemainingSpace(slot: Int): Int {
        val stack = getStack(slot)
        if (stack.isEmpty) return getSlotCapacity(slot)
        return stack.maxStackSize - stack.count
    }

    /**
     * 检查槽位是否为空
     */
    fun isSlotEmpty(slot: Int): Boolean {
        if (slot !in 0..<slotCount) return true
        return getStack(slot).isEmpty
    }

    /**
     * 检查槽位是否已满
     */
    fun isSlotFull(slot: Int): Boolean {
        if (slot !in 0..<slotCount) return true
        val stack = getStack(slot)
        if (stack.isEmpty) return false
        return stack.count >= stack.maxStackSize
    }
}