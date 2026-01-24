package xyz.luobo.mindustry.core.machine

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.items.ItemHandlerHelper.insertItem
import net.neoforged.neoforge.items.ItemStackHandler
import xyz.luobo.mindustry.core.energy.MachineEnergyStorage

abstract class BaseMachineBE(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : BlockEntity(type, pos, state) {
    var isWorking: Boolean = false

    // 组件
    // 能量存储 (核心特性：Mindustry 机器通常有内部缓冲)
    protected abstract val energyStorage: MachineEnergyStorage

    // 物品存储 (输入/输出槽位)
    protected abstract val itemHandler: ItemStackHandler

    // --- 状态数据 ---
    var progress: Int = 0
    abstract val maxProgress: Int // 来自配方或配置
    abstract val energyPerTick: Int

    // 缓存配置：容量等
    protected open val capacity: Int = 10000
    protected open val maxTransfer: Int = 100

    // --- 核心 Tick 逻辑 ---

    /**
     * 服务端 Tick：处理逻辑、生产、能量消耗
     */
    open fun tickServer() {
        // 1. 验证是否激活 (Mindustry 逻辑: 只有满足条件才工作)
        if (!canWork()) {
            if (progress > 0) decayProgress()
            isWorking = false
            return
        }

        // 2. 消耗能量
        if (energyStorage.energyStored >= energyPerTick) {
            isWorking = true
            energyStorage.extractEnergy(energyPerTick, false)
            progress++

            // 3. 完成工作
            if (progress >= maxProgress) {
                finishWork()
                progress = 0
            }
        }

        // 4. 自动弹出产物 (Mindustry 特性)
        tryAutoEject()

        setChanged()
    }

    /**
     * 检查机器是否满足工作条件 (输入充足、输出未满、红石信号等)
     */
    protected abstract fun canWork(): Boolean

    /**
     * 工作完成时的逻辑 (消耗输入，产生输出)
     */
    protected abstract fun finishWork()

    /**
     * Mindustry 特有的自动向周围/指定方向弹出物品逻辑
     */
    protected open fun tryAutoEject() {
        // 获取输出方向 (Mindustry 机器通常有固定输出口，或全向输出)
        val ejectDirs = getOutputDirections()

        for (dir in ejectDirs) {
            val neighborPos = worldPosition.relative(dir)
            // 获取邻居的 ItemHandler Capability
            val neighborCap = level?.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, dir.opposite)

            if (neighborCap != null) {
                // 遍历当前机器的所有槽位，尝试将物品输出到邻居
                for (slotIndex in 0 until itemHandler.slots) {
                    val stackInSlot = itemHandler.getStackInSlot(slotIndex)

                    // 只处理非空且为输出槽的物品（这里假设输出槽有一些判断逻辑）
                    if (isOutputSlot(slotIndex) && !stackInSlot.isEmpty) {
                        // 尝试插入到邻居的物品处理器中
                        val remainder = insertItem(neighborCap, stackInSlot, true)

                        // 如果邻居能够接受部分或全部物品，则实际执行转移
                        if (remainder != stackInSlot) {
                            // 实际插入物品
                            val extractedStack =
                                itemHandler.extractItem(slotIndex, stackInSlot.count - remainder.count, false)
                            val actualInserted = insertItem(neighborCap, extractedStack, false)

                            // 如果有未能插入的部分，放回原槽位
                            if (!actualInserted.isEmpty) {
                                itemHandler.insertItem(slotIndex, actualInserted, false)
                            }

                            break // 一次只处理一个槽位，避免过度操作
                        }
                    }
                }
                // 具体实现需遍历 outputSlots 并 insertItem
            }
        }
    }

    abstract fun isOutputSlot(slot: Int): Boolean

    protected open fun getOutputDirections(): List<Direction> = Direction.entries.toList()

    private fun decayProgress() {
        if (progress > 0) progress--
    }

    // --- 数据同步 (Packets & NBT) ---

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putInt("Energy", energyStorage.energyStored)
        tag.putInt("Progress", progress)
        tag.putBoolean("IsWorking", isWorking)
        tag.put("Inventory", itemHandler.serializeNBT(registries))
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        if (tag.contains("Energy")) energyStorage.receiveEnergy(tag.getInt("Energy"), false)
        progress = tag.getInt("Progress")
        isWorking = tag.getBoolean("IsWorking")
        itemHandler.deserializeNBT(registries, tag.getCompound("Inventory"))
    }

    // 用于客户端同步的简化包
    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket? {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        return saveWithoutMetadata(registries)
    }

    // 简单的同步触发器
    fun syncData() {
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }
}