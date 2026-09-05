package xyz.luobo.mturrets.core.machine

import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.items.ItemHandlerHelper

/**
 * 产物自动弹出(issue 73 spec):产物结算进 Buffer 后向四邻居的标准 IItemHandler 转移。
 *
 * 与 Mindustry 单一 `Building.dump()` 引擎同构(`BuildingComp.dump`):邻居自身的 IItemHandler
 * 天然充当 `acceptItem` 谓词,本 helper 只做"探测 → 转移";收不走的留在 Buffer(满载停摆)。
 * 四向轮转、无方向配置(无 GUI 的方向配置是死路,钻头可接上方漏斗)。
 *
 * 产物判定由调用方谓词给出(窑炉 = 配方产物、钻头 = 矿石)。单机结构、每 tick 调用,成本极低:
 * 探测用 simulate(simulate=true),接受才执行;零分配(余量由 insertItemStacked 返回既不新建)。
 */
object ProductEjector {

    /**
     * 尝试把 [cap] 中所有「通过 [isProduct] 判定」的物品向四邻居容器转移一轮。
     * 转移成功的物品从 Buffer 移除;收不走的原地保留。【服务端调用】
     *
     * @return 本 tick 是否发生转移(供调用方节流同步)
     */
    fun eject(
        level: Level,
        anchorPos: BlockPos,
        cap: xyz.luobo.mturrets.core.capability.IItemCapability,
        isProduct: (ItemStack) -> Boolean
    ): Boolean {
        var moved = false
        for (slot in 0 until cap.slotCount) {
            val stack = cap.getStack(slot)
            if (stack.isEmpty || !isProduct(stack)) continue
            val remainder = putIntoNeighbor(level, anchorPos, stack, cap)
                ?: continue // 无容纳容器:原地保留
            val ok = remainder.count < stack.count
            if (ok) {
                cap.setStack(slot, remainder)
                moved = true
            }
        }
        return moved
    }

    /** 四邻居轮转:任一能收下 [stack] 的容器执行转移,返回其未收下的余量;全拒返回 null(原地保留)。 */
    private fun putIntoNeighbor(
        level: Level,
        anchorPos: BlockPos,
        stack: ItemStack,
        source: xyz.luobo.mturrets.core.capability.IItemCapability
    ): ItemStack? {
        for (dir in DIRECTIONS) {
            val neighbor = level.getCapability(
                Capabilities.ItemHandler.BLOCK,
                anchorPos.relative(dir),
                dir.opposite
            ) ?: continue
            // 跳过结构自身成员:2×2 锚点的成员格把物品能力路由回本锚点 Buffer(同一实例),
            // 若当作外部容器会把产物在同一 Buffer 内打转。对位 Mindustry proximity 排除 self。
            if (neighbor === source) continue
            // 探测:simulate 跨槽堆叠,余量 < 原量 → 能收
            val after = ItemHandlerHelper.insertItemStacked(neighbor, stack, true)
            if (after.count < stack.count) {
                return ItemHandlerHelper.insertItemStacked(neighbor, stack, false)
            }
        }
        return null
    }

    private val DIRECTIONS = listOf(
        net.minecraft.core.Direction.DOWN,
        net.minecraft.core.Direction.NORTH,
        net.minecraft.core.Direction.SOUTH,
        net.minecraft.core.Direction.WEST,
        net.minecraft.core.Direction.EAST,
        net.minecraft.core.Direction.UP
    )
}