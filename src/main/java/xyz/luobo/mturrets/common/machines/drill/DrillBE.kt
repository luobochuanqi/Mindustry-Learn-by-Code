package xyz.luobo.mturrets.common.machines.drill

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.Containers
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.items.ItemHandlerHelper
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.common.ModBlocks
import xyz.luobo.mturrets.common.ModItems
import xyz.luobo.mturrets.common.items.Materials
import xyz.luobo.mturrets.core.MTurretsModBlockEntity
import xyz.luobo.mturrets.core.capability.impl.FluidCapabilityImpl
import xyz.luobo.mturrets.core.capability.impl.ItemCapabilityImpl
import xyz.luobo.mturrets.core.machine.HummingMachine
import xyz.luobo.mturrets.core.structure.Blueprint
import xyz.luobo.mturrets.core.structure.BlueprintAnchor
import xyz.luobo.mturrets.core.structure.BlueprintAnchorBlock

/**
 * 机械钻头 BE(#35 基础 + #50 区域开采,逻辑挖矿):2×2 蓝图锚点,不占能耗。
 *
 * 开采语义(#50 spec,取代 #35 采口 1×1 逐格咬柱):
 * 扫描域 = 2×2 足迹同心外扩一圈的 4×4 水平柱面,从锚点正下方扫到世界底,统计铜/铅/煤各自 Reserve;
 * 只消费目标矿格(吞掉 → 回填宿主石头 y<0 deepslate / 否则 stone,世界无缝),石头/空气/流体不碰,穿孔零成本。
 * 目标 = Lock 指定矿种,无 Lock 取数量最多者(平手按注册序铜>铅>煤);节奏按目标 Reserve 数 n 分四档
 * (n 越多挖越快,上游 Mindustry dominantItems 同构、尺度按 3D 重定标),水 Booster 乘 ×1.6 后仍四档。
 * 某矿种 n ≥ INFINITE_THRESHOLD(≈1.5–2 条矿脉 blob 交汇)视为无限矿:该矿种零消费、永采不完、Jade 显 ∞(借鉴 Create 抽岩浆)。
 * 扫描无状态:只在事件时机重扫(放置/区块加载、每产出一件、切 Lock、空闲低频复查),无缓存列表、无 blockChanged 监听。
 * 产物入内 Buffer(准入 = 三种矿石物品),满载停转;空手右键取 Buffer,手持任意模组矿石右键循环切 Lock(与手持种类无关)。
 */
class DrillBE(pos: BlockPos, state: BlockState) :
    MTurretsModBlockEntity(ModBlockEntityTypes.DRILL.get(), pos, state), BlueprintAnchor, HummingMachine {
    companion object {
        /** Buffer 槽位数:与窑炉一致,只存三种矿石物品。 */
        const val BUFFER_SLOTS = 20
        /** 内罐容量(mB):一桶恰好充满。 */
        const val WATER_TANK_CAPACITY = 1000
        /** 每物品耗水(mB):一桶 ≈ 40 物品 ≈ 50 秒,数值可调。 */
        const val WATER_PER_ITEM = 25

        /** 节奏分档粒度 s(上游 dominantItems 同构,尺度按 3D 重定标):档位 = min(ceil(n/s), 4)。 */
        const val TIER_DIVIDER = 4
        /** 无限矿阈值 T:某矿种域内 Reserve ≥ T → 该矿种零消费无限产(≈1.5–2 条 blob 交汇,#24 blob size 48)。 */
        const val INFINITE_THRESHOLD = 24

        /** 干钻节奏码表(tick/物品),下标 = 档位-1;干钻 40/⌈n/4⌉ 封顶 4 档。 */
        val DRY_TICKS_PER_ITEM = intArrayOf(40, 20, 13, 10)
        /** 水加成节奏码表 = 干钻 ÷1.6 取整(Booster ×1.6 语义,#35 延续)。 */
        val WATER_TICKS_PER_ITEM = intArrayOf(25, 13, 8, 7)

        /** 空闲态(无目标)低频重扫间隔:外部补矿/挖矿靠它自愈,不逐 tick 扫。 */
        const val IDLE_RESCAN_TICKS = 20

        /**
         * 4×4 扫描域 = 2×2 足迹同心外扩一圈;锚点居 +X/+Z 角,故相对锚点范围 (−1,−1)–(2,2)
         * 不对称(中心在足迹中心而非锚点)。
         */
        const val REGION_MIN = -1
        const val REGION_MAX = 2

        /**
         * 采集语义:矿石方块 → 产物物品(代码表;一期三种矿石全可钻,无硬质门控)。
         * 插入序 = Lock 循环序与平手序(铜→铅→煤),[LOCK_ORDER] 由此派生,勿乱序。
         */
        private val ORE_OUTPUTS: Map<Block, Item> = mapOf(
            ModBlocks.ORE_COPPER.get() to ModItems.getMaterial(Materials.COPPER).get(),
            ModBlocks.ORE_LEAD.get() to ModItems.getMaterial(Materials.LEAD).get(),
            ModBlocks.ORE_COAL.get() to ModItems.getMaterial(Materials.COAL).get()
        )

        /** Lock 循环序 = 产物注册序(铜→铅→煤),Jade 三行同序。 */
        val LOCK_ORDER: List<Item> = ORE_OUTPUTS.values.toList()

        /** 产物物品 → 码表下标(Reserve 计数槽位)。 */
        private val ORE_INDEX: Map<Item, Int> = ORE_OUTPUTS.values.withIndex().associate { (i, v) -> v to i }

        /** 矿石方块 → 码表下标。 */
        private val ORE_BLOCK_INDEX: Map<Block, Int> =
            ORE_OUTPUTS.entries.withIndex().associate { (i, e) -> e.key to i }
    }

    override val currentBlueprint: Blueprint
        get() = (blockState.block as BlueprintAnchorBlock).blueprint
    override val itemCapability: ItemCapabilityImpl =
        createItemCapability(slotCount = BUFFER_SLOTS, isValidItem = { _, stack -> isOreItem(stack) })

    override val fluidCapability: FluidCapabilityImpl =
        createFluidCapability(
            capacity = WATER_TANK_CAPACITY,
            maxReceive = WATER_TANK_CAPACITY,
            maxExtract = 0,
            isValidFluid = { it.fluid == Fluids.WATER }
        )

    private var progress = 0

    /** 空闲重扫节拍(只在无目标时推进,见 [tickServer])。 */
    private var idleTicks = 0

    /**
     * 运转态(客户端 hum 消费,#57):存在可采目标 ∧ Buffer 可再收(即本 tick 实际在挖)。
     * 服务端权威、随 update tag 同步;客户端不做口径判断,只读。
     */
    override var isRunning: Boolean = false
        private set

    // 显示面读数(Jade #52/#50 消费;右键循环切换行为归本 BE)

    /** 当前 Lock 的矿种;null = 无 Lock(默认采数量最多的矿种)。设置即重扫并同步。 */
    var oreLock: Item? = null
        set(value) {
            if (field != value) {
                field = value
                refreshScan()
                syncData()
            }
        }

    /** 分矿种 Reserve(码表序铜/铅/煤),服务端权威,扫描时重算;客户端值随 update tag 覆盖。 */
    private var reserves = IntArray(LOCK_ORDER.size)

    /** 手持任意模组矿石物品即推进一步 Lock 循环(与手持种类无关,#50 spec);null→铜→铅→煤→null。 */
    fun cycleLock() {
        oreLock = LOCK_ORDER.getOrNull(LOCK_ORDER.indexOf(oreLock) + 1)
    }

    /** 某矿种的 Reserve(域内剩余块数);未知矿种返回 0。 */
    fun oreReserve(item: Item): Int = ORE_INDEX[item]?.let { reserves[it] } ?: 0

    /** 某矿种是否处于无限态(Reserve ≥ T);无限矿零消费、Jade 显 ∞。 */
    fun isInfinite(item: Item): Boolean = oreReserve(item) >= INFINITE_THRESHOLD

    /** Buffer 准入:只存三种矿石物品(钻头产物;放料通道对无配方输入的钻头无意义)。 */
    fun isOreItem(stack: ItemStack): Boolean = ORE_OUTPUTS.containsValue(stack.item)

    /** 空手右键取出:Buffer 里全是产物,取首槽整组。 */
    fun takeBufferStack(): ItemStack? {
        val cap = itemCapability
        for (slot in 0 until cap.slotCount) {
            val stack = cap.getStack(slot)
            if (!stack.isEmpty) return cap.extractItem(slot, stack.count, false)
        }
        return null
    }

    /**
     * 当前被采矿种:Lock 指定,无 Lock 取 Reserve 最多者(平手按码表序铜>铅>煤);皆 0 返回 null(停摆)。
     */
    private fun targetOre(): Item? {
        val locked = oreLock
        if (locked != null) {
            return if (oreReserve(locked) > 0) locked else null
        }
        var best: Item? = null
        var bestCount = 0
        for (item in LOCK_ORDER) {
            val c = oreReserve(item)
            if (c > bestCount) {
                best = item
                bestCount = c
            }
        }
        return best
    }

    /**
     * 服务端 tick:单 progress 全局计时,节奏 = 被采矿种 Reserve 数 n 查四档码表(水 Booster 换表)。
     * 每完成一个周期产出一件:非无限矿按扫描序吞掉第一个被采矿格并回填宿主石头,随后重扫;
     * 满载预检不过 → 停转不吞矿。无目标时低频重扫,外部补矿/挖矿自愈。
     */
    fun tickServer() {
        val lv = level ?: return
        // 产物自动弹出(#73):每 tick 把矿石产物向四邻居标准 IItemHandler 转移,收不走的留在 Buffer。
        // 与窑炉共用 ProductEjector;isProduct = 三种矿石。
        if (xyz.luobo.mturrets.core.machine.ProductEjector.eject(lv, worldPosition, itemCapability, ::isOreItem)) {
            setChanged()
        }
        val target = targetOre()
        // 满载停转:模拟插入无位则不吞矿、进度保持(取出后自动续转)
        val room = target != null && ItemHandlerHelper.insertItem(itemCapability, ItemStack(target), true).isEmpty
        // 运转态(客户端 hum):仅在切换时同步,避免每 tick 发包
        if (room != isRunning) {
            isRunning = room
            syncData()
        }
        if (!room) {
            // 空闲态低频重扫:玩家补矿/挖矿后自愈,不逐 tick 扫
            if (target == null && ++idleTicks >= IDLE_RESCAN_TICKS) {
                idleTicks = 0
                refreshScan()
            }
            return
        }
        val item = target!!
        idleTicks = 0
        val n = oreReserve(item)
        val infinite = n >= INFINITE_THRESHOLD
        val boosted = fluidCapability.currentFluid.amount >= WATER_PER_ITEM
        // 档位 = min(ceil(n/4), 4),n ≥ 1 已由 room 保证
        val tier = (n - 1) / TIER_DIVIDER + 1
        val speed = (if (boosted) WATER_TICKS_PER_ITEM else DRY_TICKS_PER_ITEM)
            .getOrElse(tier - 1) { DRY_TICKS_PER_ITEM.last() }
        if (++progress < speed) return
        progress = 0
        if (!infinite) {
            // 非无限矿:按扫描序吞第一个目标矿格,回填宿主石头(浅层 stone / 深层 deepslate)
            val mouth = scanFirstOre(lv, item)
            if (mouth == null) {
                // 缓存计数过期(外部挖空):重扫后本周期放弃结算,不凭空产物品
                refreshScan()
                return
            }
            lv.setBlock(mouth, hostStoneFor(mouth), 3)
            refreshScan()
        }
        if (boosted) drainFluidInternal(WATER_PER_ITEM)
        val leftover = ItemHandlerHelper.insertItem(itemCapability, ItemStack(item), false)
        if (!leftover.isEmpty) {
            // 预检已保证有位,理论不可达;确定性兜底不丢物品
            Containers.dropItemStack(lv, worldPosition.x + 0.5, worldPosition.y + 0.5, worldPosition.z + 0.5, leftover)
        }
        setChanged()
    }

    private fun hostStoneFor(pos: BlockPos): BlockState =
        if (pos.y < 0) Blocks.DEEPSLATE.defaultBlockState() else Blocks.STONE.defaultBlockState()

    /**
     * 重扫 4×4 柱域,刷新分矿种 Reserve;计数变化才同步(事件节流,不逐 tick 发包)。
     */
    private fun refreshScan() {
        val lv = level
        if (lv !is ServerLevel) return
        val newCounts = IntArray(LOCK_ORDER.size)
        for (pos in regionPositions(lv)) {
            val idx = ORE_BLOCK_INDEX[lv.getBlockState(pos).block] ?: continue
            newCounts[idx]++
        }
        val changed = newCounts.contentEquals(reserves).not()
        if (changed) {
            reserves = newCounts
            syncData()
        }
    }

    /**
     * 扫描序取第一个指定矿种的矿格(表面先吃、柱面不留洞)。
     */
    private fun scanFirstOre(lv: Level, ore: Item): BlockPos? {
        val index = ORE_INDEX[ore] ?: return null
        return regionPositions(lv).firstOrNull { ORE_BLOCK_INDEX[lv.getBlockState(it).block] == index }
    }

    /**
     * 扫描序遍历 4×4 柱域:深度浅→深,层内 x→z。计数与消费共用同一序列,
     * 顺序是消费面可见行为(先吃浅处),改序会改变"哪块矿先被吞"。
     */
    private fun regionPositions(lv: Level): Sequence<BlockPos> = sequence {
        val from = worldPosition.y - 1
        for (y in from downTo lv.minBuildHeight) {
            for (x in REGION_MIN..REGION_MAX) {
                for (z in REGION_MIN..REGION_MAX) {
                    yield(BlockPos(worldPosition.x + x, y, worldPosition.z + z))
                }
            }
        }
    }

    /** 首次放置/区块重载即给客户端准确初值,不必等首个采掘周期;客户端值随 update tag 覆盖,不重算。 */
    override fun onLoad() {
        super.onLoad()
        if (level is ServerLevel) refreshScan()
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putInt("drill_progress", progress)
        tag.putString("drill_ore_lock", oreLock?.let { BuiltInRegistries.ITEM.getKey(it).toString() } ?: "")
        tag.putIntArray("drill_ore_reserves", reserves)
        tag.putBoolean("drill_is_running", isRunning)
    }
    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        progress = tag.getInt("drill_progress")
        oreLock = tag.getString("drill_ore_lock").takeIf { it.isNotEmpty() }?.let {
            ResourceLocation.tryParse(it)?.let { id -> BuiltInRegistries.ITEM.getOptional(id).orElse(null) }
        }
        // 旧档单值 drill_reserves 不迁移(#50 spec):存量钻头节奏与读数复位
        val saved = tag.getIntArray("drill_ore_reserves")
        for (i in reserves.indices) reserves[i] = saved.getOrElse(i) { 0 }
        isRunning = tag.getBoolean("drill_is_running")
    }

    // 客户端同步(#57 运转 hum):沿用窑炉/炮台同型——getUpdateTag 走 saveWithoutMetadata,
    // 使 isRunning(及 progress/oreLock/reserves)经 update tag 达客户端 BE,客户端只读不重算。
    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket =
        ClientboundBlockEntityDataPacket.create(this)

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag =
        saveWithoutMetadata(registries)
}
