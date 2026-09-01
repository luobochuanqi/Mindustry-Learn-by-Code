package xyz.luobo.mturrets.core.power

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import xyz.luobo.mturrets.core.structure.StructuralBlock
import kotlin.math.min

/**
 * 电网图对象(ADR-0007):成员 = 在场电网构件锚点 BE,聚合电量/容量为增量缓存
 * (电池充放实时加减账)。供需按 Mindustry 串行语义结算(打磨期修订,#49):每 tick 首个
 * 申报点(生产或需求)结算——生产先满足需求,生产超出需求的盈余按空余容量比例充电池,
 * 未被吸收的生产本 tick 末消失(不结转);仍不足则全体按 ratio = 供给/需求 棕停。空闲图
 * 零结算;图对象只在放置/拆除/加载时的染色(BFS)中建立,稳态零扫描;不持久化,存档只含
 * 各 BE 自身电量,重载后染色推导重建。
 *
 * 生产是成员属性([PowerMemberBE.productionPerTick]),结算点聚合——非事件流,故无论
 * 生产者/需求方谁先 tick,结算结果一致(顺序无关)。结算 = 聚合生产 → 按空余容量比例充
 * 电池(图内瞬时,镜像 [withdraw])→ 余量留在 [production] 供需求方 [requestDrain] 抽取。
 */
class PowerGraph {
    private val members = HashSet<PowerMemberBE>()

    /** 聚合余量(增量缓存 = Σ 在场电池电量);耗电侧每 tick 首个申报处在此处取样。 */
    var energy = 0
        private set
    var capacity = 0
        private set

    private var settledTick = -1L

    /** 本 tick 结算后的生产余量:已聚合、未被充电/需求方抽取的部分。每 tick 结算点重新
     * 聚合(= 各成员 [PowerMemberBE.productionPerTick] 之和),故天然不结转。 */
    private var production = 0

    /**
     * 生产者每 tick 调用的结算入口:本 tick 未结算则触发(聚合生产、按空余容量比例充电池),
     * 已结算则幂等返回。生产是成员属性、非事件流,故此入口只负责"确保本 tick 已结算",
     * 不携带任何量。无需求方时,这是充电路径的唯一入口(需求方 [requestDrain] 同样触发
     * 结算,谁先 tick 谁生效——tick 顺序无关)。
     */
    fun ensureSettled(gameTime: Long) {
        if (gameTime == settledTick) return
        settledTick = gameTime
        settle()
    }

    /**
     * 需求方每 tick 申报需求,返回本 tick 实际可取量。串行语义:grant = min(需求, 生产余量+池),
     * 生产余量先扣、缺口再走 [withdraw] 抽电池;grant 按可用总量截断,杜绝透支。首个
     * 申报点触发结算(若本 tick 尚未结算)。ponytail: 单 tick 单点结算对单需求方精确
     * (一期唯一需求方是窑炉);多需求方同 tick 的公平分摊须先全量收账再算总需求,待
     * 第二个耗电结构落地时改。
     */
    fun requestDrain(gameTime: Long, demand: Int): Int {
        if (demand <= 0) return 0
        if (gameTime != settledTick) {
            settledTick = gameTime
            settle()
        }
        val available = production + energy
        val grant = min(demand, available)
        if (grant <= 0) return 0
        val fromProduction = min(production, grant)
        production -= fromProduction
        val deficit = grant - fromProduction
        if (deficit > 0) withdraw(deficit)
        return grant
    }

    /** 成员入网:离开旧图(若有)、汇总储能增量,图引用改挂本图。染色/归队的唯一入图口。 */
    fun onMemberJoin(member: PowerMemberBE) {
        if (member.graph === this) return
        member.graph?.onMemberLeave(member)
        members += member
        energy += member.batteryEnergy
        capacity += member.batteryCapacity
        member.graph = this
    }

    /** 成员离网(破坏/卸载/重染):储能增量扣减;空图随最后一个成员自然废弃。 */
    fun onMemberLeave(member: PowerMemberBE) {
        if (!members.remove(member)) return
        energy -= member.batteryEnergy
        capacity -= member.batteryCapacity
        member.graph = null
    }

    /** 电池对外充放 → 聚合余量实时加减账(增量缓存,无重扫)。 */
    fun onBatteryDelta(delta: Int) {
        energy += delta
    }

    /**
     * 每 tick 单点结算:聚合全部成员生产(生产是成员属性,非事件),按空余容量比例把
     * 生产充入电池(图内瞬时,镜像 [withdraw] 的分摊),实充量从 [production] 扣减——
     * 未吸收的(电池满/无电池)留在余量里供需求方抽取,下一 tick 重新聚合时自然消失
     * (不结转)。
     */
    private fun settle() {
        production = members.sumOf { it.productionPerTick }
        if (production > 0) charge(production)
    }

    /**
     * 按各电池空余容量比例分摊充电,末位电池兜尾(确定性;镜像 [withdraw] 策略)。
     * 图内瞬时(直改余额、绕对外限速,与 [withdraw] 对称);实充量回扣 [production]
     * (未吸收的留在余量里)。
     */
    private fun charge(amount: Int) {
        val batteries = members
            .filter { it.batteryCapacity > 0 && it.batteryEnergy < it.batteryCapacity }
            .sortedBy { it.blockPos.asLong() }
        if (batteries.isEmpty()) return
        val totalFree = batteries.sumOf { it.batteryCapacity - it.batteryEnergy }.coerceAtLeast(1)
        var remaining = amount
        for (battery in batteries.dropLast(1)) {
            if (remaining <= 0) return
            val free = battery.batteryCapacity - battery.batteryEnergy
            val share = min(free, min(remaining, amount * free / totalFree))
            if (share > 0) {
                battery.chargeFromGrid(share)
                production -= share
                remaining -= share
            }
        }
        if (remaining > 0) {
            val last = batteries.last()
            val free = last.batteryCapacity - last.batteryEnergy
            val actual = min(remaining, free)
            last.chargeFromGrid(actual)
            production -= actual
        }
    }

    /**
     * 按各电池余量比例分摊扣减,末位电池兜尾(确定性;舍入尾差由兜尾吸收,
     * 不足 1 FE 时沉没——#30 spec 允定的确定性策略)。分母取进入扣账前的池快照:
     * 比例与扣账互不干扰,否则池随扣账收缩会放大后序份额、造成超额扣减。
     */
    private fun withdraw(amount: Int) {
        var remaining = amount
        // 储能角色 = 图上唯一有 batteryCapacity 的构件;位置序保证确定性
        val batteries = members.filter { it.batteryCapacity > 0 }.sortedBy { it.blockPos.asLong() }
        if (batteries.isEmpty()) return
        val total = energy.coerceAtLeast(1)
        for (battery in batteries.dropLast(1)) {
            if (remaining <= 0) return
            val share = min(battery.batteryEnergy, min(remaining, amount * battery.batteryEnergy / total))
            if (share > 0) {
                battery.drainFromGrid(share)
                remaining -= share
            }
        }
        if (remaining > 0) {
            batteries.last().drainFromGrid(min(remaining, batteries.last().batteryEnergy))
        }
    }
}

/**
 * 电网染色(ADR-0007,对位 Create `RotationPropagator`):放置/破坏/加载时对受影响
 * 连通块整块一次 BFS 重染,稳态零扫描。BFS 以幸存成员为新源向外扩,遇已染色同类
 * 即并入新图(被重染成员经 [PowerGraph.onMemberJoin] 离旧图,旧图随成员清零废弃)。
 * 区块卸载不做图手术(成员离场仅扣减聚合缓存),重载归队即重建,无失同步面。
 */
object PowerGraphs {

    /** 对 pos 六邻域内每个连通块各建一张全新图。 */
    fun recolorAround(level: ServerLevel, pos: BlockPos) {
        val visited = HashSet<BlockPos>()
        for (direction in Direction.values()) {
            val start = resolveAnchorPos(level, pos.relative(direction)) ?: continue
            if (!visited.add(start)) continue
            val graph = PowerGraph()
            val queue = ArrayDeque<BlockPos>()
            queue.add(start)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                val member = level.getBlockEntity(current) as? PowerMemberBE ?: continue
                graph.onMemberJoin(member)
                for (neighborDirection in Direction.values()) {
                    val neighbor = resolveAnchorPos(level, current.relative(neighborDirection)) ?: continue
                    if (visited.add(neighbor)) queue.add(neighbor)
                }
            }
        }
    }

    /**
     * 邻接(六面)连通;成员格经编码偏移解析回锚点(多方块经成员格贴节点即接入,
     * ADR-0003 能力路由同款解析)。Mindustry 无线半径 5 tile 按换算表 ÷8 取整
     * 即邻接,无线隔空连线无玩法意义,不做。
     */
    private fun resolveAnchorPos(level: ServerLevel, pos: BlockPos): BlockPos? {
        if (level.getBlockEntity(pos) is PowerMemberBE) return pos
        val state = level.getBlockState(pos)
        if (state.block is StructuralBlock) {
            val anchorPos = pos.subtract(StructuralBlock.decodeOffset(state))
            if (level.getBlockEntity(anchorPos) is PowerMemberBE) return anchorPos
        }
        return null
    }
}
