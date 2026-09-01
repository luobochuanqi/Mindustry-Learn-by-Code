package xyz.luobo.mturrets.common.power

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.common.ModItems
import xyz.luobo.mturrets.common.items.Materials
import xyz.luobo.mturrets.core.capability.IItemCapability
import xyz.luobo.mturrets.core.power.PowerMemberBE
import xyz.luobo.mturrets.core.structure.Blueprint
import xyz.luobo.mturrets.core.structure.BlueprintAnchor
import xyz.luobo.mturrets.core.structure.BlueprintAnchorBlock

/**
 * 燃烧发电机 BE(#56):1×1 蓝图锚点 + 单格燃料槽(仅模组煤,总量 [FUEL_CAPACITY])。
 * 每块煤烧 [BURN_TICKS] tick、期间产 [PRODUCTION_PER_TICK] FE/t 入电网(800 FE/煤)。
 *
 * 产量是电网成员属性(燃烧中 [PRODUCTION_PER_TICK]、空闲 0),结算走既有单点聚合
 * (Power Source 同款生产面,数值口径 ADR-0007 修订,全链路 Int)。无能量/液体 capability。
 *
 * 点燃时即从槽弹出(离开槽面)→ 拆机只散落未燃储备、不掉落燃烧中煤(spec grill 定案;
 * contentsToScatter 默认拷贝槽面即可)。结算在推进计时之前,保证每块煤恰好产 [BURN_TICKS] tick。
 */
class CombustionGeneratorBE(
    pos: BlockPos,
    state: BlockState
) : PowerMemberBE(ModBlockEntityTypes.COMBUSTION_GENERATOR.get(), pos, state), BlueprintAnchor {

    companion object {
        /** 单煤燃烧时长(MC tick);× [PRODUCTION_PER_TICK] = 800 FE/煤。 */
        const val BURN_TICKS = 40
        /** 燃烧中每 tick 产量(FE)。 */
        const val PRODUCTION_PER_TICK = 20
        /** 燃料槽总量上限(煤块数)。 */
        const val FUEL_CAPACITY = 8
    }

    private val coalItem: Item get() = ModItems.getMaterial(Materials.COAL).get()

    /** 燃料槽(单格、仅煤、总量 8);槽内为未燃储备,燃烧中煤已弹出。 */
    val fuelStorage = FuelStorage(FUEL_CAPACITY, { onContentsChanged(0) }) { stack -> stack.`is`(coalItem) }

    override val currentBlueprint: Blueprint
        get() = (blockState.block as BlueprintAnchorBlock).blueprint

    override val itemCapability: IItemCapability
        get() = fuelStorage

    /** 当前燃烧剩余 tick;>0 = 正在烧(已弹出一块煤)。持久化。 */
    private var burnRemaining = 0


    /** 当前燃烧剩余 tick(Jade 读数,#52 显示面);0 = 空闲。 */
    val burnTicksLeft: Int get() = burnRemaining
    val isBurning: Boolean get() = burnRemaining > 0

    /** 未燃储备(块数;Jade "燃料" 读数,#52 消费)。 */
    val fuelCount: Int get() = fuelStorage.count

    override val productionPerTick: Int
        get() = if (isBurning) PRODUCTION_PER_TICK else 0

    /**
     * 服务端每 tick:点燃(空闲且有储备)→ 结算本 tick 产量入网(燃烧中)→ 推进计时。
     * 结算在计时推进之前,故每块煤恰好产 [BURN_TICKS] tick;结算入口与 Power Source 同款
     * (ensureSettled,顺序无关)。
     */
    fun tickServer() {
        val lv = level ?: return
        val wasBurning = isBurning
        if (!isBurning && fuelStorage.count > 0) {
            popOneCoal()
            burnRemaining = BURN_TICKS
        }
        if (isBurning) {
            graph?.ensureSettled(lv.gameTime)
            burnRemaining = (burnRemaining - 1).coerceAtLeast(0)
        }
        if (wasBurning != isBurning) syncData() else setChanged()
    }

    /** 从槽取一块煤点燃(弹出入燃烧态,离开槽面)。调用方已校验 count>0。 */
    private fun popOneCoal() {
        val stack = fuelStorage.getStack(0)
        if (stack.isEmpty) return
        if (stack.count <= 1) fuelStorage.setStack(0, ItemStack.EMPTY) else stack.shrink(1)
        fuelStorage.onContentsChanged(0)
    }

    /**
     * 右键放煤:收下得下的(总量 8 强制)、shrink 手持到实际接受量;满量/非煤整拒。
     * 返回是否收了一部分(供 Block 层选 CONSUME / PASS_TO_DEFAULT)。
     */
    fun insertFuel(stack: ItemStack): Boolean {
        if (!stack.`is`(coalItem)) return false
        val rest = fuelStorage.insertItem(0, stack, false)
        val accepted = stack.count - rest.count
        if (accepted > 0) stack.shrink(accepted)
        return accepted > 0
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries) // 基类保存 fuelStorage(单格)
        tag.putInt("gen_burn_remaining", burnRemaining)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        burnRemaining = tag.getInt("gen_burn_remaining")
    }

    /** 客户端同步:燃烧态(isBurning + 剩余 tick + 燃料)随 update tag 推到客户端(火粒子/Jade 取数)。 */
    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket =
        ClientboundBlockEntityDataPacket.create(this)

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag =
        saveWithoutMetadata(registries)
}
