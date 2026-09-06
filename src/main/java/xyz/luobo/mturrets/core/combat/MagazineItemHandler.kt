package xyz.luobo.mturrets.core.combat

import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.IItemHandler

/**
 * 炮台供弹的能力槽面(issue 73 spec):把 IItemHandler 语义翻译到 Magazine 单位账。
 *
 * 单槽、slot 0 = 弹药注入口:`insertItem` 按"整件 → ×unitMultiplier 单位"折算(余量不足
 * 一件拒收),与手持右键装弹 [#46] 同一口径,供溜槽/漏斗/管道等标准 capability 互操作;
 * [Magazine] 多弹种分账、LIFO 选弹天然保持。
 *
 * `extractItem` 恒返回空(炮台不可退弹,对位 Mindustry ItemTurret.removeStack = 0)。
 */
class MagazineItemHandler(private val turret: TurretBE) : IItemHandler {

    override fun getSlots(): Int = 1
    override fun getStackInSlot(slot: Int): ItemStack = ItemStack.EMPTY
    override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
        if (slot != 0 || stack.isEmpty) return stack
        val ammo = turret.ammoTypeFor(stack.item) ?: return stack
        val accepted = turret.magazine.acceptedFor(stack.item, stack.count, ammo.unitMultiplier)
        if (accepted <= 0) return stack
        if (!simulate) {
            // 能力路径不经玩家(管道/漏斗无喂弹人视角):归属维持装填前记录(可能为 null);
            // 经 tryLoadAmmo 统一走"入账 + syncData"(#61,Jade 读数即时)。
            turret.tryLoadAmmo(stack)
        }
        return if (accepted < stack.count) stack.copyWithCount(stack.count - accepted) else ItemStack.EMPTY
    }
    override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack = ItemStack.EMPTY
    override fun getSlotLimit(slot: Int): Int = Int.MAX_VALUE

    override fun isItemValid(slot: Int, stack: ItemStack): Boolean =
        slot == 0 && turret.ammoTypeFor(stack.item) != null
}