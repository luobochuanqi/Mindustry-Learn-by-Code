package xyz.luobo.mindustry.common.machines.kiln

import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mindustry.client.renderers.MachineRenderer
import xyz.luobo.mindustry.common.ModBlockEntityTypes
import xyz.luobo.mindustry.common.ModItems
import xyz.luobo.mindustry.common.items.Materials
import xyz.luobo.mindustry.core.machine.BaseMachineBE

class KilnBE(
    pos: BlockPos,
    state: BlockState
) : BaseMachineBE(ModBlockEntityTypes.KILN_BLOCK_ENTITY.get(), pos, state) {
    // 缓存物品引用以提高性能
    private val leadItem by lazy { ModItems.getMaterial(Materials.LEAD).get() }
    private val sandItem by lazy { ModItems.getMaterial(Materials.SAND).get() }
    private val metaglassItem by lazy { ModItems.getMaterial(Materials.METAGLASS).get() }

    // 配置属性
    override val itemSlotCount: Int = 3 // 两输入 + 一输出
    override val energyCapacity: Int = 10000
    override val maxProgress: Int = 10
    override val energyPerTick: Int = 2

    // 重写物品验证
    override fun isInputSlot(slot: Int): Boolean {
        return slot == 0 || slot == 1
    }

    override fun isOutputSlot(slot: Int): Boolean {
        return slot == 2
    }

    // 重写物品槽验证逻辑（在 tickServer 中使用）
    override fun tickServer() {
        // 在工作前检查物品有效性
        val itemStack0 = itemHandler.getStack(0)
        val itemStack1 = itemHandler.getStack(1)

        // 验证物品类型
        if (!itemStack0.isEmpty && itemStack0.item != leadItem && itemStack0.item != sandItem) {
            // 无效物品，弹出
            itemHandler.extractItem(0, itemStack0.count, false)
        }
        if (!itemStack1.isEmpty && itemStack1.item != leadItem && itemStack1.item != sandItem) {
            // 无效物品，弹出
            itemHandler.extractItem(1, itemStack1.count, false)
        }

        super.tickServer()
    }

    override fun canWork(): Boolean {
        val itemStack0 = itemHandler.getStack(0)
        val itemStack1 = itemHandler.getStack(1)
        val itemStack2 = itemHandler.getStack(2)

        // 检查物品栈是否为空
        if (itemStack0.isEmpty || itemStack1.isEmpty) {
            return false
        }

        // 检查输出槽位是否已满
        if (itemStack2.count >= 64) {
            return false
        }

        // 使用缓存的物品引用进行比较
        return (itemStack0.item == leadItem && itemStack1.item == sandItem) ||
                (itemStack0.item == sandItem && itemStack1.item == leadItem)
    }

    override fun finishWork() {
        // 验证输入槽位是否有足够的物品
        val itemStack0 = itemHandler.getStack(0)
        val itemStack1 = itemHandler.getStack(1)

        if (itemStack0.count >= 1 && itemStack1.count >= 1) {
            // 消耗输入物品
            itemHandler.extractItem(0, 1, false)
            itemHandler.extractItem(1, 1, false)

            // 添加输出物品
            val itemStack2 = itemHandler.getStack(2)
            if (itemStack2.isEmpty) {
                itemHandler.setStack(2, ItemStack(metaglassItem, 1))
            } else {
                itemHandler.setStack(2, ItemStack(metaglassItem, itemStack2.count + 1))
            }
        }
    }

    // 渲染相关
    override fun onLoad() {
        super.onLoad()
        MachineRenderer.addToRenderList(worldPosition)
    }

    override fun setRemoved() {
        if (level?.isClientSide == true) {
            MachineRenderer.removeFromRenderList(worldPosition)
        }
        super.setRemoved()
    }

    override fun onChunkUnloaded() {
        if (level?.isClientSide == true) {
            MachineRenderer.removeFromRenderList(worldPosition)
        }
        super.onChunkUnloaded()
    }
}