package xyz.luobo.mturrets.core.power

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mturrets.core.MTurretsModBlockEntity

/**
 * 电网构件锚点 BE 基类(ADR-0007):持有运行时图引用,生命周期钩子收口图维护——
 * 放置/加载(成员进世界)整块重染,破坏(真移除)重染分裂,区块卸载只做聚合扣减、
 * 不做图手术(重载归队即重建)。图对象不持久化,本类零存档字段。
 */
abstract class PowerMemberBE(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : MTurretsModBlockEntity(type, pos, state) {

    /** 当前所在图(运行时);由 [PowerGraph.onMemberJoin]/[onMemberLeave] 维护。 */
    var graph: PowerGraph? = null

    /** 储能角色视图:非储能构件(节点/耗电结构)恒为 0;电池覆写为实值。 */
    open val batteryEnergy: Int get() = 0
    open val batteryCapacity: Int get() = 0

    /** 生产者角色视图:每 tick 常量产量(FE),结算点聚合;非生产者恒为 0。 */
    open val productionPerTick: Int get() = 0

    /** 图按比例扣账时的储能侧入口;非储能构件为空操作。 */
    open fun drainFromGrid(amount: Int) {}

    /** 图按比例充电时的储能侧入口;非储能构件为空操作。图内瞬时(与 [drainFromGrid] 对称)。 */
    open fun chargeFromGrid(amount: Int) {}

    /** 区块卸载标记:卸载路径的 setRemoved 不触发重染(卸载不是拓扑变化)。 */
    private var unloadedByChunk = false

    override fun onLoad() {
        super.onLoad()
        unloadedByChunk = false
        val serverLevel = level
        if (serverLevel is ServerLevel) PowerGraphs.recolorAround(serverLevel, worldPosition)
    }

    override fun onChunkUnloaded() {
        super.onChunkUnloaded()
        unloadedByChunk = true
        graph?.onMemberLeave(this)
    }

    override fun setRemoved() {
        val serverLevel = level
        graph?.onMemberLeave(this)
        // 真破坏(非卸载):受影响连通块以幸存成员为新源重染,断点两侧当场分裂
        if (!unloadedByChunk && serverLevel is ServerLevel) {
            PowerGraphs.recolorAround(serverLevel, worldPosition)
        }
        super.setRemoved()
    }
}