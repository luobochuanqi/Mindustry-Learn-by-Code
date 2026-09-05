package xyz.luobo.mturrets.core.combat

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * 炮台弹仓 = 按弹种分账的单位账(ADR-0009):不存物理物品,只记 (弹种 → 单位数)。
 * 容量按单位计;选弹后入为主(LIFO);扳机扣 1 单位;拆机按 floor(单位/倍率) 折回物品。
 */
class Magazine(private val cap: Int) {

    /** 弹种账目(队尾 = 后入为主,扣账与选弹都取队尾)。 */
    data class Entry(val item: Item, var units: Int, val unitMultiplier: Int)

    private val entries = mutableListOf<Entry>()

    /** 剩余总单位数 */
    var total: Int = 0
        private set

    /** 队尾弹种(当前选弹);空仓为 null。 */
    val tail: Entry? get() = entries.lastOrNull()

    /** 当前容量内可接受的整件数(不改状态;供能力槽面 simulate 探测与 [load] 共用同一折算)。
     * 向下取整到整件:不足一件的余量(free % mult)保持空着,不造免费弹药(守恒)。 */
    fun acceptedFor(item: Item, count: Int, unitMultiplier: Int): Int {
        val free = cap - total
        if (free <= 0 || count <= 0 || unitMultiplier <= 0) return 0
        return minOf(count, free / unitMultiplier)
    }

    /** 部分装弹:按剩余容量向下取整到整件,返回接受件数(0 = 整堆拒收,物品原样保留,#46 取代 #31)。 */
    fun load(item: Item, count: Int, unitMultiplier: Int): Int {
        val acceptedItems = acceptedFor(item, count, unitMultiplier)
        if (acceptedItems <= 0) return 0
        val units = acceptedItems * unitMultiplier
        val existing = entries.firstOrNull { it.item == item }
        if (existing != null) {
            existing.units += units
            // LIFO:已存在弹种补仓后挪到队尾(后入为主)
            entries.remove(existing)
            entries.add(existing)
        } else {
            entries.add(Entry(item, units, unitMultiplier))
        }
        total += units
        return acceptedItems
    }

    /** 扳机扣 1 单位(队尾优先);不足返回 false。 */
    fun drainOne(): Boolean {
        val e = entries.lastOrNull() ?: return false
        if (e.units < 1) return false
        e.units -= 1
        total -= 1
        if (e.units == 0) entries.removeAt(entries.size - 1)
        return true
    }

    /** 是否至少有一发可用。 */
    fun canFire(): Boolean = entries.any { it.units >= 1 }

    /** 拆除折回:floor(单位/倍率) 的物品散落(余 1 单位不折,确定性优先)。 */
    fun toItems(): List<ItemStack> =
        entries.mapNotNull { e ->
            val n = e.units / e.unitMultiplier
            if (n <= 0) null else ItemStack(e.item, n)
        }

    // ========== NBT ==========

    fun save(tag: CompoundTag, registries: HolderLookup.Provider) {
        val list = ListTag()
        for (e in entries) {
            val t = CompoundTag()
            t.put("item", ItemStack(e.item).save(registries))
            t.putInt("units", e.units)
            t.putInt("mult", e.unitMultiplier)
            list.add(t)
        }
        tag.put("magazine", list)
    }

    fun load(tag: CompoundTag, registries: HolderLookup.Provider) {
        entries.clear()
        total = 0
        val list = tag.getList("magazine", Tag.TAG_COMPOUND.toInt())
        for (i in 0 until list.size) {
            val t = list.getCompound(i)
            val item = ItemStack.parse(registries, t.getCompound("item"))
                .orElse(ItemStack.EMPTY).item
            if (item == Items.AIR) continue // 存档里被删的弹种丢弃,对齐 Mindustry 语义
            val units = t.getInt("units")
            val mult = t.getInt("mult").coerceAtLeast(1)
            entries.add(Entry(item, units, mult))
            total += units
        }
    }
}