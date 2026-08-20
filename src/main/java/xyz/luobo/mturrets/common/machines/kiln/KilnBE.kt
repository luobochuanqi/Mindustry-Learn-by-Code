package xyz.luobo.mturrets.common.machines.kiln

import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mturrets.client.renderers.MachineRenderer
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.common.ModItems
import xyz.luobo.mturrets.common.items.Materials
import xyz.luobo.mturrets.core.machine.BaseMachineBE

/**
 * 窑炉方块实体
 * 将铅和沙子合成为金属玻璃
 */
class KilnBE(
    pos: BlockPos,
    state: BlockState
) : BaseMachineBE(ModBlockEntityTypes.KILN_BLOCK_ENTITY.get(), pos, state) {

    companion object {
        // 配置常量
        const val INPUT_SLOT_1 = 0
        const val INPUT_SLOT_2 = 1
        const val OUTPUT_SLOT = 2

        const val ENERGY_CAPACITY = 10000
        const val ENERGY_PER_TICK = 2
        const val MAX_PROGRESS = 10
        const val MAX_OUTPUT_STACK_SIZE = 64
    }

    // ========== 配置属性 ==========

    override val itemSlotCount: Int = 3
    override val energyCapacity: Int = ENERGY_CAPACITY
    override val maxProgress: Int = MAX_PROGRESS
    override val energyPerTick: Int = ENERGY_PER_TICK

    override val maxEnergyReceive: Int = 200
    override val maxEnergyExtract: Int = 200

    // ========== 物品引用缓存 ==========

    private val leadItem by lazy { ModItems.getMaterial(Materials.LEAD).get() }
    private val sandItem by lazy { ModItems.getMaterial(Materials.SAND).get() }
    private val metaglassItem by lazy { ModItems.getMaterial(Materials.METAGLASS).get() }

    // ========== 槽位配置 ==========

    override fun isInputSlot(slot: Int): Boolean {
        return slot == INPUT_SLOT_1 || slot == INPUT_SLOT_2
    }

    override fun isOutputSlot(slot: Int): Boolean {
        return slot == OUTPUT_SLOT
    }

    /**
     * 检查物品是否可以插入到指定槽位（不考虑当前库存状态）
     * 输入槽只能接受铅和沙子，输出槽不能插入
     */
    override fun isValidItemForSlot(slot: Int, stack: ItemStack): Boolean {
        if (stack.isEmpty) return false

        return when (slot) {
            INPUT_SLOT_1, INPUT_SLOT_2 -> {
                // 输入槽只能接受铅或沙子
                stack.item == leadItem || stack.item == sandItem
            }

            OUTPUT_SLOT -> {
                // 输出槽不允许插入
                false
            }

            else -> true
        }
    }

    // ========== 工作逻辑 ==========

    override fun canWork(): Boolean {
        // 检查能量是否充足
        if (!energyCapability.hasEnergy(energyPerTick)) {
            return false
        }

        val stack1 = itemCapability.getStack(INPUT_SLOT_1)
        val stack2 = itemCapability.getStack(INPUT_SLOT_2)
        val outputStack = itemCapability.getStack(OUTPUT_SLOT)

        // 检查输入槽位是否有物品
        if (stack1.isEmpty || stack2.isEmpty) {
            return false
        }

        // 检查输出槽位是否已满
        if (outputStack.count >= MAX_OUTPUT_STACK_SIZE) {
            return false
        }

        // 检查配方是否匹配（铅 + 沙子）
        return hasValidRecipe(stack1, stack2)
    }

    /**
     * 检查是否有有效的配方
     */
    private fun hasValidRecipe(stack1: ItemStack, stack2: ItemStack): Boolean {
        val isLeadAndSand = (stack1.item == leadItem && stack2.item == sandItem)
        val isSandAndLead = (stack1.item == sandItem && stack2.item == leadItem)
        return isLeadAndSand || isSandAndLead
    }

    override fun finishWork() {
        val stack1 = itemCapability.getStack(INPUT_SLOT_1)
        val stack2 = itemCapability.getStack(INPUT_SLOT_2)

        // 再次验证配方
        if (!hasValidRecipe(stack1, stack2)) {
            return
        }

        // 消耗输入物品（直接修改物品栈，因为输入槽不允许 extractItem）
        stack1.shrink(1)
        if (stack1.isEmpty) {
            itemCapability.setStack(INPUT_SLOT_1, ItemStack.EMPTY)
        }

        stack2.shrink(1)
        if (stack2.isEmpty) {
            itemCapability.setStack(INPUT_SLOT_2, ItemStack.EMPTY)
        }

        // 添加输出物品
        val outputStack = itemCapability.getStack(OUTPUT_SLOT)
        if (outputStack.isEmpty) {
            itemCapability.setStack(OUTPUT_SLOT, ItemStack(metaglassItem, 1))
        } else {
            outputStack.grow(1)
        }

        // 标记为已更改
        setChanged()
    }

    // ========== 渲染相关 ==========

    override fun onLoad() {
        super.onLoad()
        if (level?.isClientSide == true) {
            MachineRenderer.addToRenderList(worldPosition)
        }
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