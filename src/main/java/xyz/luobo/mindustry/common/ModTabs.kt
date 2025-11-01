package xyz.luobo.mindustry.common

import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters
import net.minecraft.world.item.CreativeModeTabs
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mindustry.Mindustry
import xyz.luobo.mindustry.common.items.Materials
import java.util.function.Supplier

object ModTabs {
    val CREATIVE_MODE_TABS: DeferredRegister<CreativeModeTab?> =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Mindustry.MOD_ID)

    val EXAMPLE_TAB: DeferredHolder<CreativeModeTab?, CreativeModeTab?> = CREATIVE_MODE_TABS.register(
        "example_tab",
        Supplier {
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.mindustry"))
                .withTabsBefore(CreativeModeTabs.COMBAT)
                .icon { ModItems.EXAMPLE_ITEM.get().defaultInstance }
                .displayItems { parameters: ItemDisplayParameters?, output: CreativeModeTab.Output? ->
                    output!!.accept(ModItems.EXAMPLE_ITEM.get())
                }.build()
        })

    // 在这里添加物品到 创造标签页
    fun addCreative(event: BuildCreativeModeTabContentsEvent) {
        if (event.tabKey === EXAMPLE_TAB.getKey()) {
            event.accept(ModItems.POWER_NODE_BLOCK_ITEM.get())
            event.accept(ModItems.GRAPHITE_PRESS_BLOCK_ITEM.get())
            Materials.ALL.forEach { material ->
                event.accept(ModItems.getMaterial(material).get())
            }
        }
    }

    fun register() {
        CREATIVE_MODE_TABS.register(MOD_BUS)
    }
}