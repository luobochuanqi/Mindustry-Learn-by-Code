package xyz.luobo.mindustry.common.machines.kiln

import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.items.ItemStackHandler
import xyz.luobo.mindustry.common.ModBlockEntityTypes
import xyz.luobo.mindustry.common.ModItems
import xyz.luobo.mindustry.common.items.Materials
import xyz.luobo.mindustry.core.energy.MachineEnergyStorage
import xyz.luobo.mindustry.core.itemHandler.MachineItemHandler
import xyz.luobo.mindustry.core.machine.BaseMachineBE

class KilnBE(
    pos: BlockPos,
    state: BlockState
) : BaseMachineBE(ModBlockEntityTypes.KILN_BLOCK_ENTITY.get(), pos, state) {
    // 缓存物品引用以提高性能
    private val leadItem by lazy { ModItems.getMaterial(Materials.LEAD).get() }
    private val sandItem by lazy { ModItems.getMaterial(Materials.SAND).get() }
    private val metaglassItem by lazy { ModItems.getMaterial(Materials.METAGLASS).get() }
    
    // 两输入 一输出
    public override val itemHandler: ItemStackHandler = object : MachineItemHandler(3, this) {
        override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
            when (slot) {
                0 -> {
                    return stack.item == leadItem
                }

                1 -> {
                    return stack.item == sandItem
                }

                2 -> {
                    return false  // 输出槽不能手动插入物品
                }
            }
            return super.isItemValid(slot, stack)
        }
    }
    public override val energyStorage = object : MachineEnergyStorage(40, this) {}
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

        // 使用缓存的物品引用进行比较
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

            // 使用缓存的物品引用创建 ItemStack
            val outputItemStack = ItemStack(metaglassItem, 1)

            // 尝试插入到输出槽位（索引为2）
            itemHandler.insertItem(2, outputItemStack, false)
        }
    }

    override fun isOutputSlot(slot: Int): Boolean {
        when (slot) {
            2 -> {
                return true
            }
        }
        return false
    }
}