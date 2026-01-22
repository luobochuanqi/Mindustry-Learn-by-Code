package xyz.luobo.mindustry.core.itemHandler

import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.ItemStackHandler
import xyz.luobo.mindustry.core.machine.BaseMachineBE

open class MachineItemHandler(
    slots: Int,
    val be: BaseMachineBE
) : ItemStackHandler(slots) {
    override fun onContentsChanged(slot: Int) {
        be.setChanged()
        be.syncData()
    }

    override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack {
        // super 中包含同样语句, 考虑是否删除
        this.validateSlotIndex(slot)
        if (!be.isOutputSlot(slot)) {
            return ItemStack.EMPTY
        }
        return super.extractItem(slot, amount, simulate)
    }
}