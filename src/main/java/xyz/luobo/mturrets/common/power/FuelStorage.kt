package xyz.luobo.mturrets.common.power

import net.minecraft.world.item.ItemStack
import xyz.luobo.mturrets.core.capability.IItemCapability
import kotlin.math.min

/**
 * 燃料槽(#56 燃烧发电机):单格、仅收模组煤、总量上限 [capacity] 在插入面强制
 * (ItemCapability 本身无容量参数)。右键 insertItem+shrink 的"收下得下的、shrink 到
 * 实际接受量"模式与窑炉输入一致;部分装入留手持余量,满量整拒。
 *
 * 直接实现 [IItemCapability](ItemCapabilityImpl 为 final 不可扩展);仅持一个未燃煤栈。
 * 基类 saveAdditional/loadAdditional 经 slotCount+getStack/setStack 自动持久化。
 */
class FuelStorage(
    capacity: Int,
    private val onContentsChanged: () -> Unit,
    private val isFuel: (ItemStack) -> Boolean
) : IItemCapability {

    /** 燃料总量上限(块数)。 */
    val capacity: Int = capacity

    private var stack: ItemStack = ItemStack.EMPTY

    /** 槽内当前燃料数(块)。 */
    val count: Int get() = stack.count

    override val slotCount: Int = 1

    override fun getStack(slot: Int): ItemStack = if (slot == 0) stack else ItemStack.EMPTY

    override fun setStack(slot: Int, item: ItemStack) {
        if (slot != 0) return
        stack = item
    }

    override fun onContentsChanged(slot: Int) {
        onContentsChanged()
    }

    override fun isValidItemForSlot(slot: Int, item: ItemStack): Boolean = isFuel(item)

    override fun isItemValid(slot: Int, item: ItemStack): Boolean =
        slot == 0 && isFuel(item)

    override fun canInsert(slot: Int): Boolean = slot == 0

    override fun canExtract(slot: Int): Boolean = slot == 0

    override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack {
        if (slot != 0 || amount <= 0 || stack.isEmpty) return ItemStack.EMPTY
        val toExtract = min(amount, stack.count)
        if (!simulate) {
            if (stack.count <= toExtract) stack = ItemStack.EMPTY else stack.shrink(toExtract)
            onContentsChanged(0)
        }
        return stack.copyWithCount(toExtract)
    }

    /**
     * 插入面强制总量上限:非燃料整拒;可收量 = min(请求, 剩余容量),0 则整拒。
     * 部分收时 shrink 留手持余量,满收时整收。返回未收下的余量(与 IItemHandler 语义一致)。
     */
    override fun insertItem(slot: Int, item: ItemStack, simulate: Boolean): ItemStack {
        if (slot != 0 || item.isEmpty || !isFuel(item)) return item
        val room = (capacity - count).coerceAtLeast(0)
        val accepted = min(item.count, room)
        if (accepted <= 0) return item
        if (!simulate) {
            if (stack.isEmpty) stack = item.copyWithCount(accepted) else stack.grow(accepted)
            onContentsChanged(0)
        }
        return if (accepted < item.count) item.copyWithCount(item.count - accepted) else ItemStack.EMPTY
    }

    override fun getSlotLimit(slot: Int): Int = if (slot == 0) capacity else 0

    override fun getSlotCapacity(slot: Int): Int = if (slot == 0) capacity else 0

    override fun getRemainingSpace(slot: Int): Int =
        if (slot == 0) (capacity - count).coerceAtLeast(0) else 0

    override fun isSlotEmpty(slot: Int): Boolean = slot == 0 && stack.isEmpty

    override fun isSlotFull(slot: Int): Boolean = slot == 0 && count >= capacity
}
