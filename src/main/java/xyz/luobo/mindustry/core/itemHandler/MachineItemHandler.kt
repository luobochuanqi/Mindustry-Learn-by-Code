package xyz.luobo.mindustry.core.itemHandler

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
}