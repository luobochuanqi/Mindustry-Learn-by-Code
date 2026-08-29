package xyz.luobo.mturrets.common

import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.CreativeModeTabs
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mturrets.MTurrets
import xyz.luobo.mturrets.common.items.Materials
import xyz.luobo.mturrets.common.liquids.Liquids
import java.util.function.Supplier

object ModTabs {
    val CREATIVE_MODE_TABS: DeferredRegister<CreativeModeTab?> =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MTurrets.MOD_ID)

    val EXAMPLE_TAB: DeferredHolder<CreativeModeTab?, CreativeModeTab?> = CREATIVE_MODE_TABS.register(
        "main_tab",
        Supplier {
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.mturrets"))
                .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                .icon { ModItems.DUO_BLOCK_ITEM.get()!!.defaultInstance }
                .build()
        })

    // 在这里添加物品到 创造标签页
    fun addCreative(event: BuildCreativeModeTabContentsEvent) {
        if (event.tabKey === EXAMPLE_TAB.getKey()) {
            event.accept(ModItems.POWER_NODE_ITEM.get())
            event.accept(ModItems.BATTERY_ITEM.get())
            event.accept(ModItems.KILN_ITEM.get())
            event.accept(ModItems.DUO_BLOCK_ITEM.get())
            event.accept(ModItems.ARC_BLOCK_ITEM.get())
            event.accept(ModItems.MELTDOWN_BLOCK_ITEM.get())
            event.accept(ModItems.TEST_STRUCTURE_ANCHOR_2X2_ITEM.get())
            Materials.ALL.forEach { material ->
                event.accept(ModItems.getMaterial(material).get())
            }
            Liquids.ALL.forEach { liquid ->
                event.accept(ModItems.getLiquid(liquid).get())
            }
        }
    }



    fun register() {
        CREATIVE_MODE_TABS.register(MOD_BUS)
    }
}