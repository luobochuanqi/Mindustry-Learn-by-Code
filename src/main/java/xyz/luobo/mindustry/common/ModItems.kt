package xyz.luobo.mindustry.common

import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mindustry.Mindustry

object ModItems {
    val MOD_ITEMS: DeferredRegister.Items = DeferredRegister.createItems(Mindustry.MOD_ID)

    val EXAMPLE_ITEM: DeferredItem<Item> = MOD_ITEMS.registerSimpleItem("example_item")

    val POWER_NODE_BLOCK_ENTITY_ITEM: DeferredItem<BlockItem?> =
        MOD_ITEMS.registerSimpleBlockItem(ModBlocks.POWER_NODE_BLOCK)

    fun register() {
        MOD_ITEMS.register(MOD_BUS)
    }
}