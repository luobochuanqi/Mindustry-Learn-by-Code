package xyz.luobo.mturrets.common.machines.drill

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.Containers
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
import xyz.luobo.mturrets.core.structure.Blueprint
import xyz.luobo.mturrets.core.structure.BlueprintAnchor
import xyz.luobo.mturrets.core.structure.BlueprintAnchorBlock

/**
 * 机械钻头 BE(#35):2×2 蓝图锚点,采口 = 锚点正下方 1×1 格(#35 spec 用户裁决)。
 * 采集语义(#24):每产出 1 物品吞掉采口矿石格、回填宿主石头(y<0 → deepslate,否则 stone),矿脉会采空;
 * 无能耗;内罐水为 Booster(CONTEXT 词表):有水按 25 tick/物品、耗 25 mB 结算,干罐回落 40 tick 基础节奏。
 * 产物入内 Buffer(准入 = 三种矿石物品),满载停转(不吞矿、不推进);
 * 采口非矿石则停摆不报错,补料(新矿/重新放置)后续转。
 */
class DrillBE(pos: BlockPos, state: BlockState) :
    MTurretsModBlockEntity(ModBlockEntityTypes.DRILL.get(), pos, state), BlueprintAnchor {

    companion object {
        /** Buffer 槽位数:与窑炉一致,只存三种矿石物品。 */
        const val BUFFER_SLOTS = 20
        /** 内罐容量(mB):一桶恰好充满。 */
        const val WATER_TANK_CAPACITY = 1000
        /** 基础节奏(#24):2 秒/物品。 */
        const val BASE_TICKS_PER_ITEM = 40
        /** 水加成节奏:40 / 1.6(#24 水 boost ×1.6 → 1.25 秒/物品)。 */
        const val WATER_TICKS_PER_ITEM = 25
        /** 每物品耗水(mB):一桶 ≈ 40 物品 ≈ 50 秒,数值可调。 */
        const val WATER_PER_ITEM = 25

        /** 采口矿石方块 → 产物物品(代码表;一期三种矿石全可钻,无硬质门控)。 */
        private val ORE_OUTPUTS: Map<Block, Item> = mapOf(
            ModBlocks.ORE_COPPER.get() to ModItems.getMaterial(Materials.COPPER).get(),
            ModBlocks.ORE_LEAD.get() to ModItems.getMaterial(Materials.LEAD).get(),
            ModBlocks.ORE_COAL.get() to ModItems.getMaterial(Materials.COAL).get()
        )
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
     * 服务端 tick:采口为矿石才推进;每 tick 按罐内水量取节奏(满 25 mB → 水加成),
     * 满程结算 = 吞矿回填宿主石头 + 产出入 Buffer;Buffer 满载预检不过 → 停转不吞矿。
     */
    fun tickServer() {
        val lv = level ?: return
        val mouth = worldPosition.below()
        val oreItem = ORE_OUTPUTS[lv.getBlockState(mouth).block] ?: return
        // 满载停转:模拟插入无位则不吞矿、进度保持(取出后自动续转)
        if (!ItemHandlerHelper.insertItem(itemCapability, ItemStack(oreItem), true).isEmpty) return
        val boosted = fluidCapability.currentFluid.amount >= WATER_PER_ITEM
        val speed = if (boosted) WATER_TICKS_PER_ITEM else BASE_TICKS_PER_ITEM
        if (++progress < speed) return
        progress = 0
        // 采集语义:吞掉矿石格、回填宿主石头;浅层 stone / 深层 deepslate
        lv.setBlock(mouth, hostStoneFor(mouth), 3)
        if (boosted) drainFluidInternal(WATER_PER_ITEM)
        val leftover = ItemHandlerHelper.insertItem(itemCapability, ItemStack(oreItem), false)
        if (!leftover.isEmpty) {
            // 预检已保证有位,理论不可达;确定性兜底不丢物品
            Containers.dropItemStack(lv, worldPosition.x + 0.5, worldPosition.y + 0.5, worldPosition.z + 0.5, leftover)
        }
        setChanged()
    }

    private fun hostStoneFor(pos: BlockPos): BlockState =
        if (pos.y < 0) Blocks.DEEPSLATE.defaultBlockState() else Blocks.STONE.defaultBlockState()

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putInt("drill_progress", progress)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        progress = tag.getInt("drill_progress")
    }
}