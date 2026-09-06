package xyz.luobo.mturrets.common.power

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.core.power.PowerAnchorBE
import xyz.luobo.mturrets.core.power.PowerGraphs
import xyz.luobo.mturrets.core.power.PowerLinks

/**
 * 电力节点(ADR-0007,无线链路修订 #69):1×1 蓝图锚点。图成员身份由 [PowerAnchorBE] 收口;
 * [links] 由此承接无线链路端点,放置后 [autolink] 自动补链(Mindustry placed `getPotentialLinks`)。
 * 额外持有供电比例 [supplyRatioLaser](服务端计算、随 update tag 同步),供客户端激光健康色读取。
 */
class PowerNodeBE(pos: BlockPos, state: BlockState) :
    PowerAnchorBE(ModBlockEntityTypes.POWER_NODE.get(), pos, state) {

    /** 本图供电比例(0..1),客户端激光颜色读取;服务端每 tick 刷新、变化时同步。 */
    var supplyRatioLaser: Float = 1f
        private set
    private var syncedRatio = -1f

    /** 服务端每 tick:刷新本图供电比例并向客户端发送(变化时);最小化同步面。 */
    fun tickServer() {
        val lv = level ?: return
        val ratio = graph?.lastSupplyRatio ?: 1f
        if (ratio != syncedRatio) {
            syncedRatio = ratio
            supplyRatioLaser = ratio
            syncData()
        }
    }

    /** 放置自动补链:对范围内合法构件按最近优先补满(对位 Mindustry placed `getPotentialLinks`)。 */
    fun autolink() {
        val lv = level
        if (lv !is ServerLevel) return
        val candidates = PowerLinks.candidates(lv, this)
        for (other in candidates) {
            if (links.size >= PowerLinks.MAX_LINKS) break
            PowerLinks.add(lv, this, other)
        }
        if (links.isNotEmpty()) {
            syncData()
            PowerGraphs.recolorAround(lv, worldPosition)
        }
    }

    /** 与本构件切换一条链路(有则拆、无则建)。供交互层与 GameTest 直接驱动。 */
    fun toggleLink(other: xyz.luobo.mturrets.core.power.PowerMemberBE): Boolean {
        val lv = level
        if (lv !is ServerLevel) return false
        return PowerLinks.toggle(lv, this, other)
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putFloat("supply", supplyRatioLaser)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        supplyRatioLaser = tag.getFloat("supply").coerceIn(0f, 1f)
        syncedRatio = supplyRatioLaser
    }

    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket =
        ClientboundBlockEntityDataPacket.create(this)

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag =
        saveWithoutMetadata(registries)
}