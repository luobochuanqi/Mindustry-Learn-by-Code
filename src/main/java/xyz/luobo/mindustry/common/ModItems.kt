package xyz.luobo.mindustry.common

import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mindustry.Mindustry
import xyz.luobo.mindustry.common.items.DebugBaconItem
import xyz.luobo.mindustry.common.items.Materials

object ModItems {
    val MOD_ITEMS: DeferredRegister.Items = DeferredRegister.createItems(Mindustry.MOD_ID)

    val EXAMPLE_ITEM: DeferredItem<Item> = MOD_ITEMS.registerSimpleItem("example_item")

    val POWER_NODE_BLOCK_ITEM: DeferredItem<BlockItem?> =
        MOD_ITEMS.registerSimpleBlockItem(ModBlocks.POWER_NODE_BLOCK)

    // machines items
    val KILN_BLOCK_ITEM: DeferredItem<BlockItem?> =
        MOD_ITEMS.registerSimpleBlockItem(ModBlocks.KILN_BLOCK)

    // turrets items
    val DUO_BLOCK_ITEM: DeferredItem<BlockItem?> =
        MOD_ITEMS.registerSimpleBlockItem(ModBlocks.DUO_BLOCK)

    val DEBUG_BACON: DeferredItem<DebugBaconItem> =
        MOD_ITEMS.registerItem<DebugBaconItem>("debug_bacon", ::DebugBaconItem)

    val ALL_ITEMS = Materials.ALL.associateWith { material ->
        MOD_ITEMS.registerSimpleItem(material.id)
    }

    fun getMaterial(material: Materials): DeferredItem<Item> = ALL_ITEMS[material]!!

    fun register() {
        MOD_ITEMS.register(MOD_BUS)
    }
}