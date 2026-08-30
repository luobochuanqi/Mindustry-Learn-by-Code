package xyz.luobo.mturrets.common

import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mturrets.MTurrets
import xyz.luobo.mturrets.common.items.Materials
import xyz.luobo.mturrets.common.liquids.Liquids

object ModItems {
    val MOD_ITEMS: DeferredRegister.Items = DeferredRegister.createItems(MTurrets.MOD_ID)

    val POWER_NODE_ITEM: DeferredItem<BlockItem?> =
        MOD_ITEMS.registerSimpleBlockItem(ModBlocks.POWER_NODE)
    val BATTERY_ITEM: DeferredItem<BlockItem?> =
        MOD_ITEMS.registerSimpleBlockItem(ModBlocks.BATTERY)

    // machines items
    val KILN_ITEM: DeferredItem<BlockItem?> =
        MOD_ITEMS.registerSimpleBlockItem(ModBlocks.KILN)
    // 矿脉与钻头(#35)
    val ORE_COPPER_ITEM: DeferredItem<BlockItem?> =
        MOD_ITEMS.registerSimpleBlockItem(ModBlocks.ORE_COPPER)
    val ORE_LEAD_ITEM: DeferredItem<BlockItem?> =
        MOD_ITEMS.registerSimpleBlockItem(ModBlocks.ORE_LEAD)
    val ORE_COAL_ITEM: DeferredItem<BlockItem?> =
        MOD_ITEMS.registerSimpleBlockItem(ModBlocks.ORE_COAL)
    val DRILL_ITEM: DeferredItem<BlockItem?> =
        MOD_ITEMS.registerSimpleBlockItem(ModBlocks.DRILL)

    // turrets items
    val DUO_BLOCK_ITEM: DeferredItem<BlockItem?> =
        MOD_ITEMS.registerSimpleBlockItem(ModBlocks.DUO_BLOCK)
    // Scatter(#34)
    val SCATTER_ITEM: DeferredItem<BlockItem?> =
        MOD_ITEMS.registerSimpleBlockItem(ModBlocks.SCATTER)
    val ARC_BLOCK_ITEM: DeferredItem<BlockItem?> =
        MOD_ITEMS.registerSimpleBlockItem(ModBlocks.ARC_BLOCK)
    val MELTDOWN_BLOCK_ITEM: DeferredItem<BlockItem?> =
        MOD_ITEMS.registerSimpleBlockItem(ModBlocks.MELTDOWN_BLOCK)
    // 蓝图管线锚点物品(成员方格无物品,掉落收口在锚点,ADR-0003)
    val TEST_STRUCTURE_ANCHOR_2X2_ITEM: DeferredItem<BlockItem?> =
        MOD_ITEMS.registerSimpleBlockItem(ModBlocks.TEST_STRUCTURE_ANCHOR_2X2)

    val ALL_ITEMS = Materials.ALL.associateWith { material ->
        MOD_ITEMS.registerSimpleItem(material.id)
    }

    val ALL_LIQUIDS = Liquids.ALL.associateWith { liquid ->
        MOD_ITEMS.registerSimpleItem(liquid.id)
    }

    fun getMaterial(material: Materials): DeferredItem<Item> = ALL_ITEMS[material]!!

    fun getLiquid(liquid: Liquids): DeferredItem<Item> = ALL_LIQUIDS[liquid]!!

    fun register() {
        MOD_ITEMS.register(MOD_BUS)
    }
}