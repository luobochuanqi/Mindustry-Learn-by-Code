package xyz.luobo.mturrets.common.jade

import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.entity.BlockEntity
import snownee.jade.api.BlockAccessor
import snownee.jade.api.IBlockComponentProvider
import snownee.jade.api.ITooltip
import snownee.jade.api.IWailaClientRegistration
import snownee.jade.api.IWailaPlugin
import snownee.jade.api.WailaPlugin
import snownee.jade.api.config.IPluginConfig
import xyz.luobo.mturrets.MTurrets
import xyz.luobo.mturrets.common.machines.drill.DrillBE
import xyz.luobo.mturrets.common.machines.drill.DrillBlock
import xyz.luobo.mturrets.common.machines.kiln.KilnBE
import xyz.luobo.mturrets.common.machines.kiln.KilnBlock
import xyz.luobo.mturrets.common.power.BatteryBE
import xyz.luobo.mturrets.common.power.BatteryBlock
import xyz.luobo.mturrets.common.power.CombustionGeneratorBE
import xyz.luobo.mturrets.common.power.CombustionGeneratorBlock
import xyz.luobo.mturrets.common.power.PowerSourceBE
import xyz.luobo.mturrets.common.turrets.DuoBlock
import xyz.luobo.mturrets.common.turrets.ScatterBlock
import xyz.luobo.mturrets.core.combat.TurretBE
import xyz.luobo.mturrets.core.structure.StructuralBlock

/**
 * Jade 信息显示插件(#52 全量补全;吸收 #37 缺口/#47/#48/#51)。
 * 锚点格直读 BE;成员格(BE-less StructuralBlock)解码偏移代理回锚点渲染(#47)。
 * 插件类两端加载(Jade 为两端依赖),tooltip 逻辑仅客户端调用。
 */
@WailaPlugin(MTurrets.MOD_ID)
class MTurretsJadePlugin : IWailaPlugin {

    override fun registerClient(registration: IWailaClientRegistration) {
        // 全部一期锚点块 + 成员格共用一个 provider(按 BE 类型分发)
        listOf(
            DuoBlock::class.java,
            ScatterBlock::class.java,
            KilnBlock::class.java,
            BatteryBlock::class.java,
            DrillBlock::class.java,
            CombustionGeneratorBlock::class.java,
            StructuralBlock::class.java
        ).forEach { registration.registerBlockComponent(StructureDataProvider, it) }
    }
}

/** 一期结构读数:炮台弹药/耐久、窑炉进度/供给比例、电池储能、钻头锁定/储量/存货。 */
object StructureDataProvider : IBlockComponentProvider {
    override fun getUid(): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(MTurrets.MOD_ID, "structure_data")

    override fun appendTooltip(tooltip: ITooltip, accessor: BlockAccessor, config: IPluginConfig) {
        val be = anchorEntity(accessor) ?: return
        when (be) {
            is TurretBE -> {
                tooltip.add(Component.translatable("jade.mturrets.ammo", be.magazine.total, be.spec.maxAmmo))
                tooltip.add(Component.translatable("jade.mturrets.health", be.health, be.spec.health))
            }
            is KilnBE -> {
                tooltip.add(Component.translatable("jade.mturrets.progress", be.progressPercent))
                // 供给比例只在棕停(<100%)时有信息量:满速是常态,不值得占一行
                if (be.supplyRatio < 1f) {
                    tooltip.add(Component.translatable("jade.mturrets.supply", percent(be.supplyRatio)))
                }
            }
            is BatteryBE -> {
                tooltip.add(Component.translatable("jade.mturrets.energy", be.batteryEnergy, be.batteryCapacity))
            }
            is PowerSourceBE -> {
                tooltip.add(Component.translatable("jade.mturrets.production", PowerSourceBE.PRODUCTION_PER_TICK))
            }
            is CombustionGeneratorBE -> {
                tooltip.add(Component.translatable("jade.mturrets.fuel", be.burnTicksLeft, be.fuelCount))
            }
            is DrillBE -> {
                val lock = be.oreLock
                tooltip.add(
                    Component.translatable(
                        "jade.mturrets.drill_lock",
                        lock?.let { Component.translatable(it.descriptionId) }
                            ?: Component.translatable("jade.mturrets.drill_lock_none")
                    )
                )
                // #50:锁定单行该矿种;未锁定三行分矿种;无限态(Reserve ≥ T)显 ∞
                val oreItems = if (lock != null) listOf(lock) else DrillBE.LOCK_ORDER
                for (ore in oreItems) {
                    val value = if (be.isInfinite(ore)) "∞" else be.oreReserve(ore).toString()
                    tooltip.add(
                        Component.translatable(
                            "jade.mturrets.drill_reserve_typed",
                            Component.translatable(ore.descriptionId),
                            value
                        )
                    )
                }
                bufferLine(tooltip, be)
            }
        }
    }

    /** 锚点 BE:成员格无 BE,按编码偏移解码找回锚点(#47);锚点格直取本格。 */
    private fun anchorEntity(accessor: BlockAccessor): BlockEntity? {
        if (accessor.block is StructuralBlock) {
            val anchor = accessor.position.subtract(StructuralBlock.decodeOffset(accessor.blockState))
            return accessor.level.getBlockEntity(anchor)
        }
        return accessor.blockEntity
    }

    /** 钻头存货摘要:按物品合并成 "铜 ×12, 铅 ×3";Component 非 CharSequence,先出字符串再入行。 */
    private fun bufferLine(tooltip: ITooltip, be: DrillBE) {
        val groups = LinkedHashMap<Item, Int>()
        val cap = be.itemCapability
        for (slot in 0 until cap.slotCount) {
            val stack = cap.getStack(slot)
            if (!stack.isEmpty) groups.merge(stack.item, stack.count, Int::plus)
        }
        if (groups.isEmpty()) return
        val text = groups.entries.joinToString(", ") { (item, count) ->
            "${Component.translatable(item.descriptionId).string} ×$count"
        }
        tooltip.add(Component.translatable("jade.mturrets.drill_buffer", text))
    }

    private fun percent(ratio: Float): Int = (ratio * 100).toInt()
}