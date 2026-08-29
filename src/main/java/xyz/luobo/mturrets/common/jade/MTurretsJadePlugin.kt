package xyz.luobo.mturrets.common.jade

import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import snownee.jade.api.BlockAccessor
import snownee.jade.api.IBlockComponentProvider
import snownee.jade.api.ITooltip
import snownee.jade.api.IWailaClientRegistration
import snownee.jade.api.IWailaPlugin
import snownee.jade.api.WailaPlugin
import snownee.jade.api.config.IPluginConfig
import xyz.luobo.mturrets.MTurrets
import xyz.luobo.mturrets.common.blocks.PowerNodeBlock
import xyz.luobo.mturrets.common.blockEntities.PowerNodeBlockEntity
import xyz.luobo.mturrets.common.turrets.ArcTurretBlock
import xyz.luobo.mturrets.common.turrets.ArcTurretBlockEntity
import xyz.luobo.mturrets.common.turrets.DuoTurretBlock
import xyz.luobo.mturrets.common.turrets.DuoTurretBlockEntity
import xyz.luobo.mturrets.common.turrets.MeltdownTurretBlock
import xyz.luobo.mturrets.common.turrets.MeltdownTurretBlockEntity
/**
 * Jade 信息显示插件
 * 炮台:弹药/能量;电力节点:能量(窑炉显示随 #37 新插件落地)
 *
 * 插件类两端加载(Jade 为两端依赖),tooltip 逻辑仅客户端调用
 */
@WailaPlugin(MTurrets.MOD_ID)
class MTurretsJadePlugin : IWailaPlugin {

    override fun registerClient(registration: IWailaClientRegistration) {
        // 炮台(物品炮台显示弹药;能量炮台显示能量)
        registration.registerBlockComponent(TurretDataProvider, DuoTurretBlock::class.java)
        registration.registerBlockComponent(TurretDataProvider, ArcTurretBlock::class.java)
        registration.registerBlockComponent(TurretDataProvider, MeltdownTurretBlock::class.java)

        // 窑炉(#37 落地新 KilnDataProvider;legacy 方块已退役,旧通道先摘)

        // 电力节点
        registration.registerBlockComponent(PowerNodeDataProvider, PowerNodeBlock::class.java)
    }
}

/** 炮台信息:弹药(物品炮台)与能量(能量炮台) */
object TurretDataProvider : IBlockComponentProvider {
    override fun getUid(): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(MTurrets.MOD_ID, "turret_data")

    override fun appendTooltip(tooltip: ITooltip, accessor: BlockAccessor, config: IPluginConfig) {
        val be = accessor.getBlockEntity()
        when (be) {
            is DuoTurretBlockEntity -> {
                tooltip.add(
                    Component.translatable(
                        "jade.mturrets.ammo",
                        be.currentAmmo,
                        be.config.maxAmmo
                    )
                )
            }

            is ArcTurretBlockEntity -> {
                tooltip.add(
                    Component.translatable(
                        "jade.mturrets.energy",
                        be.energyCapability.currentEnergy,
                        be.energyCapability.energyCapacity
                    )
                )
            }

            is MeltdownTurretBlockEntity -> {
                tooltip.add(
                    Component.translatable(
                        "jade.mturrets.energy",
                        be.energyCapability.currentEnergy,
                        be.energyCapability.energyCapacity
                    )
                )
            }
        }
    }
}


/** 电力节点信息:当前能量 */
object PowerNodeDataProvider : IBlockComponentProvider {
    override fun getUid(): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(MTurrets.MOD_ID, "power_node_data")

    override fun appendTooltip(tooltip: ITooltip, accessor: BlockAccessor, config: IPluginConfig) {
        val be = accessor.getBlockEntity()
        if (be is PowerNodeBlockEntity) {
            tooltip.add(
                Component.translatable(
                    "jade.mturrets.energy",
                    be.energyCapability.currentEnergy,
                    be.energyCapability.energyCapacity
                )
            )
        }
    }
}