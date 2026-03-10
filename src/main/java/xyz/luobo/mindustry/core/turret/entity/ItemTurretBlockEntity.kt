package xyz.luobo.mindustry.core.turret.entity

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.common.capabilities.Capability
import net.neoforged.neoforge.common.capabilities.ForgeCapabilities
import net.neoforged.neoforge.common.util.LazyOptional
import net.neoforged.neoforge.items.IItemHandler
import net.neoforged.neoforge.items.ItemStackHandler
import xyz.luobo.mindustry.core.turret.bullet.BulletType

/**
 * 物品弹药炮台实体
 * 使用物品作为弹药，不同物品对应不同的 BulletType
 * 模仿 Mindustry 的 ItemTurret
 *
 * @param type BlockEntityType
 * @param pos 方块位置
 * @param state 方块状态
 */
abstract class ItemTurretBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : ReloadTurretBlockEntity(type, pos, state) {

    // ========== 弹药映射表（子类必须实现）==========

    /**
     * 弹药映射：Item -> BulletType
     * 定义了这个炮台接受哪些物品作为弹药，以及对应的子弹属性
     */
    abstract val ammoTypes: Map<Item, BulletType>

    /**
     * 默认弹药（如果没有指定或当前弹药耗尽）
     */
    open val defaultAmmo: Item?
        get() = ammoTypes.keys.firstOrNull()

    // ========== 当前弹药状态 ==========

    /**
     * 当前使用的弹药物品
     */
    var currentAmmoItem: Item? = null
        private set

    /**
     * 当前弹药对应的 BulletType
     */
    val currentBulletType: BulletType?
        get() = currentAmmoItem?.let { ammoTypes[it] }

    // ========== 物品 Capability ==========

    /**
     * 物品存储处理器
     * 用于接收外部输入的弹药
     */
    val itemHandler: ItemStackHandler by lazy {
        object : ItemStackHandler(1) {
            override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
                return acceptsAmmo(stack.item)
            }

            override fun onContentsChanged(slot: Int) {
                this@ItemTurretBlockEntity.onContentsChanged(slot)
            }
        }
    }

    private val itemHandlerOptional: LazyOptional<IItemHandler> = LazyOptional.of { itemHandler }

    // ========== 初始化 ==========

    init {
        // 设置默认弹药
        currentAmmoItem = defaultAmmo
    }

    // ========== 弹药管理 ==========

    /**
     * 设置当前使用的弹药
     * @param item 弹药物品
     * @return 是否成功设置
     */
    fun setAmmo(item: Item): Boolean {
        if (item in ammoTypes.keys) {
            currentAmmoItem = item
            setChanged()
            return true
        }
        return false
    }

    /**
     * 检查是否接受某物品作为弹药
     * @param item 物品
     * @return 是否接受
     */
    fun acceptsAmmo(item: Item): Boolean {
        return item in ammoTypes.keys
    }

    /**
     * 获取物品的弹药属性
     * @param item 物品
     * @return BulletType 或 null
     */
    fun getBulletType(item: Item): BulletType? {
        return ammoTypes[item]
    }

    /**
     * 检查当前弹药是否有效
     */
    fun hasValidAmmo(): Boolean {
        return currentAmmoItem != null && currentAmmo >= config.ammoPerShot
    }

    // ========== 自动装填 ==========

    /**
     * 从物品栏自动装填弹药
     * @return 成功装填的数量
     */
    open fun autoReload(): Int {
        val stack = itemHandler.getStackInSlot(0)
        if (stack.isEmpty) return 0

        val item = stack.item
        if (!acceptsAmmo(item)) return 0

        // 如果当前没有设置弹药，设置为此物品
        if (currentAmmoItem == null) {
            currentAmmoItem = item
        }

        // 如果当前弹药槽满了，不装填
        if (currentAmmo >= config.maxAmmo) return 0

        // 计算可以装填的数量
        val space = config.maxAmmo - currentAmmo
        val toLoad = minOf(stack.count, space)

        // 装填
        currentAmmo += toLoad
        stack.shrink(toLoad)

        if (stack.isEmpty) {
            itemHandler.setStackInSlot(0, ItemStack.EMPTY)
        }

        setChanged()
        return toLoad
    }

    /**
     * 内容变化回调
     */
    protected open fun onContentsChanged(slot: Int) {
        setChanged()
        // 尝试自动装填
        if (currentAmmo < config.maxAmmo) {
            autoReload()
        }
    }

    // ========== 重写父类方法 ==========

    override fun canShoot(): Boolean {
        return super.canShoot() &&
                currentAmmoItem != null &&
                currentAmmo >= config.ammoPerShot
    }

    override fun consumeAmmo() {
        currentAmmo -= config.ammoPerShot
        if (currentAmmo < 0) currentAmmo = 0

        // 如果弹药耗尽，尝试从物品栏装填
        if (currentAmmo <= 0) {
            autoReload()
        }

        setChanged()
    }

    override fun hasAmmo(): Boolean {
        return currentAmmo > 0 || itemHandler.getStackInSlot(0).count > 0
    }

    // ========== Capability ==========

    override fun <T> getCapability(capability: Capability<T>, side: Direction?): LazyOptional<T> {
        return if (capability == ForgeCapabilities.ITEM_HANDLER) {
            itemHandlerOptional.cast()
        } else {
            super.getCapability(capability, side)
        }
    }

    override fun invalidateCaps() {
        super.invalidateCaps()
        itemHandlerOptional.invalidate()
    }

    // ========== 数据保存 ==========

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)

        // 保存当前弹药物品
        currentAmmoItem?.let {
            tag.putString("currentAmmoItem", it.toString())
        }

        // 保存物品栏
        tag.put("inventory", itemHandler.serializeNBT(registries))
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)

        // 恢复当前弹药物品
        if (tag.contains("currentAmmoItem")) {
            val itemName = tag.getString("currentAmmoItem")
            // 注意：这里需要通过物品注册表查找物品
            // 简化处理，实际实现需要完整解析
            val item = ammoTypes.keys.find { it.toString() == itemName }
            currentAmmoItem = item ?: defaultAmmo
        }

        // 恢复物品栏
        if (tag.contains("inventory")) {
            itemHandler.deserializeNBT(registries, tag.getCompound("inventory"))
        }
    }

    // ========== 便捷方法 ==========

    /**
     * 获取弹药填充百分比
     */
    fun getAmmoFillPercent(): Float {
        return currentAmmo.toFloat() / config.maxAmmo
    }

    /**
     * 获取当前弹药的名称
     */
    fun getCurrentAmmoName(): String {
        return currentAmmoItem?.descriptionId ?: "empty"
    }

    /**
     * 获取所有可接受的弹药列表
     */
    fun getAcceptedAmmos(): Set<Item> {
        return ammoTypes.keys
    }

    /**
     * 切换到下一种弹药
     * @return 是否成功切换
     */
    fun switchToNextAmmo(): Boolean {
        val keys = ammoTypes.keys.toList()
        if (keys.size <= 1) return false

        val currentIndex = currentAmmoItem?.let { keys.indexOf(it) } ?: -1
        val nextIndex = (currentIndex + 1) % keys.size
        val nextItem = keys[nextIndex]

        return setAmmo(nextItem)
    }
}