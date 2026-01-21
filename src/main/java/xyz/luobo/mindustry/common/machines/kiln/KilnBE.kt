package xyz.luobo.mindustry.common.machines.kiln

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.items.ItemStackHandler
import xyz.luobo.mindustry.common.ModBlockEntityTypes
import xyz.luobo.mindustry.common.ModItems
import xyz.luobo.mindustry.common.items.Materials
import xyz.luobo.mindustry.core.machine.BaseMachineBE

class KilnBE(
    pos: BlockPos,
    state: BlockState
) : BaseMachineBE(ModBlockEntityTypes.KILN_BLOCK_ENTITY.get(), pos, state) {
    // 两输入 一输出
    override val itemHandler: ItemStackHandler
        get() = ItemStackHandler(3)
    override val maxProgress: Int
        get() = 10
    override val energyPerTick: Int
        get() = 2

    override fun canWork(): Boolean {
        val itemStack0 = itemHandler.getStackInSlot(0)
        val itemStack1 = itemHandler.getStackInSlot(1)

        // 检查物品栈是否为空
        if (itemStack0.isEmpty || itemStack1.isEmpty) {
            return false
        }

        // 预先获取材料项以提高性能
        val leadItem = ModItems.getMaterial(Materials.LEAD)
        val sandItem = ModItems.getMaterial(Materials.SAND)

        // 使用明确的括号确保逻辑正确
        return ((itemStack0.item == leadItem && itemStack1.item == sandItem) ||
                (itemStack0.item == sandItem && itemStack1.item == leadItem))
    }

    override fun finishWork() {
        // 验证输入槽位是否有足够的物品
        val itemStack0 = itemHandler.getStackInSlot(0)
        val itemStack1 = itemHandler.getStackInSlot(1)

        if (itemStack0.count >= 1 && itemStack1.count >= 1) {
            itemHandler.extractItem(0, 1, false)
            itemHandler.extractItem(1, 1, false)

            // 获取输出物品的 ItemStack
            val outputItemStack = ModItems.getMaterial(Materials.METAGLASS).toStack(1)

            // 尝试插入到输出槽位（索引为2）
            itemHandler.insertItem(2, outputItemStack, false)
        }
    }
}