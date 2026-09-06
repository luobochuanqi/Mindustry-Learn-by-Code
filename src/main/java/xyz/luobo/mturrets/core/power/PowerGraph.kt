package xyz.luobo.mturrets.core.power

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import xyz.luobo.mturrets.core.structure.StructuralBlock
import kotlin.math.min
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.shapes.CollisionContext

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

    /** 本图最近一次需求结算的供电比例(0..1;无需求方时恒 1)。激光/健康色取此值
     * (Mindustry `power.graph.getSatisfaction()`),随 update tag 同步到节点客户端。 */
    var lastSupplyRatio: Float = 1f
        private set

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
        // 供电比例 = 本次实得 / 需求(Mindustry getSatisfaction 口径);健康色取此值
        lastSupplyRatio = grant.toFloat() / demand
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
 * 电网染色(ADR-0007,对位 Create `RotationPropagator`;无线链路修订 #69):放置/破坏/加载时
 * 对受影响连通块整块一次 BFS 重染,稳态零扫描。BFS 以幸存成员为新源向外扩,遇已染色
 * 同类即并入新图(被重染成员经 [PowerGraph.onMemberJoin] 离旧图,旧图随成员清零废弃)。
 * 区块卸载不做图手术(成员离场仅扣减聚合缓存),重载归队即重建,无失同步面。
 */
object PowerGraphs {

    /**
     * 对受 [pos] 影响的连通块各建一张全新图。pos 自身作起点,使无线链路断裂处与隔空
     * 连通能正确断裂/合并(既扫六邻域、也扫隔空链路远端)。
     */
    fun recolorAround(level: ServerLevel, pos: BlockPos) {
        val visited = HashSet<BlockPos>()
        val starts = ArrayList<BlockPos>(7)
        resolveAnchorPos(level, pos)?.let { starts.add(it) }
        for (direction in Direction.values()) {
            resolveAnchorPos(level, pos.relative(direction))?.let { starts.add(it) }
        }
        for (start in starts) {
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
                for (link in member.links) {
                    if (visited.add(link)) queue.add(link)
                }
            }
        }
    }

    /**
     * 邻接(六面)连通 + 无线链路连通(#69)。成员格经编码偏移解析回锚点(多方块经成员格贴
     * 节点即接入,ADR-0003 能力路由同款解析)。无线链路的范围/可见/上限判据见 [PowerLinks]。
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

/**
 * 无线链路(ADR-0007 修订 #69,对位 Mindustry `PowerNode`):范围 [RANGE] 格、每构件上限
 * [MAX_LINKS]。建链对称(两端各存一份);链路拓扑并入/分裂交由 [PowerGraphs.recolorAround]
 * 的 BFS,本对象只维护链路集 + 判据,不直接操作图。
 */
object PowerLinks {
    /** 链路最大范围(Mindustry 默认 `laserRange=6`)。 */
    const val RANGE = 6
    /** 每构件最大链路数(Mindustry 默认 `maxNodes=3`)。 */
    const val MAX_LINKS = 3

    /**
     * 范围内合法可建链构件(Mindustry `getPotentialLinks`),按"离 [from] 最近优先"排序。
     * 供放置自动补链与双击自动补满;不包含 [from] 自身。
     */
    fun candidates(level: ServerLevel, from: PowerMemberBE): List<PowerMemberBE> {
        val out = ArrayList<PowerMemberBE>()
        val center = from.blockPos
        // 以 RANGE 为界扫一个轴对称立方框(粗筛),再逐格精判 linkable
        for (dx in -RANGE..RANGE) for (dy in -RANGE..RANGE) for (dz in -RANGE..RANGE) {
            if (dx == 0 && dy == 0 && dz == 0) continue
            val pos = center.offset(dx, dy, dz)
            val member = level.getBlockEntity(pos) as? PowerMemberBE
            if (member != null && member !== from && linkable(level, from, member)) {
                out += member
            }
        }
        return out.sortedBy { chebyshev(center, it.blockPos) }
    }

    /**
     * 两构件间是否隔了不透明可挡电力的方块(Mindustry `insulated` raycast)。沿两端构件中心在多
     * 立方格间逐格步进:电网构件(节点/发电机/电池等)与自己不挡电力(对位 Mindustry 建筑透传),
     * 只有地形/墙体(有碰撞且不透明的非电网构件格)才隔断电链。
     */
    fun insulated(level: Level, from: BlockPos, to: BlockPos): Boolean {
        if (from == to) return false
        val a = from.center
        val b = to.center
        val dir = b.subtract(a).normalize()
        // 两端各内缩 0.5 跳出自身块体,再逐格步进到对方
        var cursor = a.add(dir.scale(0.5))
        val end = b.subtract(dir.scale(0.5))
        val step = dir.scale(0.35) // 小于半格保证不跳格;足够密以捕捉单格薄墙
        var steps = 0
        while (steps < 40 && cursor.distanceToSqr(end) > 0.01) {
            val cell = BlockPos.containing(cursor)
            val state = level.getBlockState(cell)
            // 电网构件格不挡电力;有碰撞的不透明非电网格挡
            if (!isPowerCell(level, cell) && !state.isAir && state.isCollisionShapeFullBlock(level, cell)) {
                return true
            }
            cursor = cursor.add(step)
            steps++
        }
        return false
    }

    /** 该格是否为电网构件(锚点 BE 或电网锚点的成员结构格)——电力透传,不挡。 */
    private fun isPowerCell(level: Level, pos: BlockPos): Boolean {
        if (level.getBlockEntity(pos) is PowerMemberBE) return true
        val state = level.getBlockState(pos)
        if (state.block is StructuralBlock) {
            val anchor = pos.subtract(StructuralBlock.decodeOffset(state))
            return level.getBlockEntity(anchor) is PowerMemberBE
        }
        return false
    }

    /** 两构件能否建链:范围/上限/未绝缘/未同连。两端任一臂满或同连即拒。 */
    private fun linkable(level: ServerLevel, a: PowerMemberBE, b: PowerMemberBE): Boolean {
        if (a === b) return false
        if (a.links.size >= MAX_LINKS || b.links.size >= MAX_LINKS) return false
        if (a.links.contains(b.blockPos) || b.links.contains(a.blockPos)) return false
        if (chebyshev(a.blockPos, b.blockPos) > RANGE) return false
        return !insulated(level, a.blockPos, b.blockPos)
    }

    /** 建立对称链路(两端各存对方坐标)。返回是否成功;成功后由调用方决定重染。 */
    fun add(level: ServerLevel, a: PowerMemberBE, b: PowerMemberBE): Boolean {
        if (!linkable(level, a, b)) return false
        a.links += b.blockPos
        b.links += a.blockPos
        a.syncData()
        b.syncData()
        return true
    }

    /** 拆除对称链路并当场重染分裂。返回是否拆到。 */
    fun remove(level: ServerLevel, a: PowerMemberBE, b: PowerMemberBE): Boolean {
        val removed = a.links.remove(b.blockPos) or b.links.remove(a.blockPos)
        if (removed) {
            a.syncData()
            b.syncData()
            PowerGraphs.recolorAround(level, a.blockPos)
        }
        return removed
    }

    /** 切换一条链路(有则拆、无则建)。 */
    fun toggle(level: ServerLevel, a: PowerMemberBE, b: PowerMemberBE): Boolean =
        if (a.links.contains(b.blockPos)) remove(level, a, b) else add(level, a, b)


    /** 清空 [member] 的全部链路(其远端对应项一并移除)。图重染由调用方负责。 */
    fun reset(level: ServerLevel, member: PowerMemberBE) {
        val cleared = member.links.toList()
        for (remote in cleared) {
            (level.getBlockEntity(remote) as? PowerMemberBE)?.links?.remove(member.blockPos)?.also {
                level.getBlockEntity(remote)?.let { r ->
                    (r as PowerMemberBE).syncData()
                }
            }
        }
        if (cleared.isNotEmpty()) {
            member.links.clear()
            member.syncData()
        }
    }
    private fun chebyshev(a: BlockPos, b: BlockPos): Int =
        maxOf(Math.abs(a.x - b.x), Math.abs(a.y - b.y), Math.abs(a.z - b.z))
}
