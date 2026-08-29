package xyz.luobo.mturrets.core.machine

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.items.ItemHandlerHelper.insertItem
import xyz.luobo.mturrets.core.MTurretsModBlockEntity
import xyz.luobo.mturrets.core.capability.IItemCapability
import xyz.luobo.mturrets.core.capability.impl.EnergyCapabilityImpl
import xyz.luobo.mturrets.core.capability.impl.ItemCapabilityImpl

/**
  * LEGACY: 翻新期机器基类;新机器按 ADR-0006 配方骨架(RecipeType/Serializer 代码注册)重建时评估复用面。
 * 机器方块实体基类
 * 使用组合方式管理 Capability（能量、物品）
 * 提供通用的机器功能：进度跟踪、能量消耗、物品处理
 */
abstract class BaseMachineBE(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : MTurretsModBlockEntity(type, pos, state) {
    var isWorking: Boolean = false

    /** 上次进度同步的 game time(节流) */
    private var lastProgressSyncTick: Long = 0

    /** 当前进度 */
    var progress: Int = 0

    /** 最大进度（来自配方或配置） */
    abstract val maxProgress: Int

    /** 每tick能量消耗 */
    abstract val energyPerTick: Int

    /** 物品槽位数 */
    abstract val itemSlotCount: Int

    /** 能量容量 */
    abstract val energyCapacity: Int

    /** 能量最大输入速率 */
    open val maxEnergyReceive: Int = 100

    /** 能量最大输出速率（通常为0） */
    open val maxEnergyExtract: Int = 0

    // ========== Capability 配置 ==========

    /**
     * 能量 Capability
     * 使用 ModBlockEntity 的能量系统
     */
    override val energyCapability: EnergyCapabilityImpl by lazy {
        createEnergyCapability(
            capacity = energyCapacity,
            maxReceive = maxEnergyReceive,
            maxExtract = maxEnergyExtract
        )
    }

    /**
     * 物品 Capability
     * 使用 ModBlockEntity 的物品系统
     */
    override val itemCapability: ItemCapabilityImpl by lazy {
        createItemCapability(
            slotCount = itemSlotCount,
            canInsert = { slot -> isInputSlot(slot) },
            canExtract = { slot -> isOutputSlot(slot) },
            isValidItem = { slot, stack -> isValidItemForSlot(slot, stack) }
        )
    }

    // ========== 便捷访问 ==========

    /**
     * 物品处理便捷访问
     */
    protected val itemHandler: IItemCapability
        get() = itemCapability!!

    /**
     * 服务端 Tick:处理逻辑、生产、能量消耗
     * 条件不满足时停摆并保持进度(恢复条件后从当前进度继续)
     */
    open fun tickServer() {
        // 1. 验证是否激活 (只有满足条件才工作)
        if (!canWork()) {
            isWorking = false
            return
        }

        // 2. 消耗能量
        if (energyCapability.hasEnergy(energyPerTick)) {
            isWorking = true
            energyCapability.extractEnergy(energyPerTick, false)
            progress++

            // 3. 完成工作
            if (progress >= maxProgress) {
                finishWork()
                progress = 0
            }
        } else {
            // 能量不足:停摆,进度保持
            isWorking = false
        }

        // 4. 自动弹出产物 (Mindustry 特性)
        tryAutoEject()

        setChanged()

        // 5. 进度/状态节流同步到客户端
        val now = level?.gameTime ?: return
        if (now - lastProgressSyncTick >= 5) {
            lastProgressSyncTick = now
            syncData()
        }
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
     * 自动向周围弹出物品
     */
    protected open fun tryAutoEject() {
        // 获取输出方向
        val ejectDirs = getOutputDirections()

        for (dir in ejectDirs) {
            val neighborPos = worldPosition.relative(dir)
            // 获取邻居的 itemCapability Capability
            val neighborCap = level?.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, dir.opposite)

            if (neighborCap != null) {
                // 遍历当前机器的所有槽位，尝试将物品输出到邻居
                for (slotIndex in 0 until itemCapability.slotCount) {
                    val stackInSlot = itemCapability.getStack(slotIndex)

                    // 只处理非空且为输出槽的物品
                    if (isOutputSlot(slotIndex) && !stackInSlot.isEmpty) {
                        // 尝试插入到邻居的物品处理器中
                        val remainder = insertItem(neighborCap, stackInSlot, true)

                        // 如果邻居能够接受部分或全部物品，则实际执行转移
                        if (remainder != stackInSlot) {
                            // 实际插入物品
                            val extractedStack =
                                itemCapability.extractItem(slotIndex, stackInSlot.count - remainder.count, false)
                            val actualInserted = insertItem(neighborCap, extractedStack, false)

                            // 如果有未能插入的部分，放回原槽位
                            if (!actualInserted.isEmpty) {
                                itemCapability.setStack(slotIndex, actualInserted)
                            }

                            break // 一次只处理一个槽位，避免过度操作
                        }
                    }
                }
            }
        }
    }

    /**
     * 获取输出方向
     */
    protected open fun getOutputDirections(): List<Direction> = Direction.entries.toList()

    /**
     * 检查物品是否可以插入到指定槽位（不考虑当前库存状态）
     * 子类可以重写此方法来限制可以插入的物品类型
     * 
     * 默认实现：输入槽和输出槽都接受所有物品
     * 
     * @param slot 槽位索引
     * @param stack 要插入的物品栈
     * @return true 如果该物品类型可以被插入到该槽位
     */
    protected open fun isValidItemForSlot(slot: Int, stack: ItemStack): Boolean = true

    // ========== 数据保存 ==========

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putInt("progress", progress)
        tag.putBoolean("isWorking", isWorking)
        // 能量和物品数据由 ModBlockEntity 自动保存
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        progress = tag.getInt("progress")
        isWorking = tag.getBoolean("isWorking")
        // 能量和物品数据由 ModBlockEntity 自动加载
    }

    // ========== 数据同步 ==========

    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket? {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        return saveWithoutMetadata(registries)
    }
}