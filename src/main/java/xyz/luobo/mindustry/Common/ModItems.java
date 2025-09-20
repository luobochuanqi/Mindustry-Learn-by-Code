package xyz.luobo.mindustry.Common;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import xyz.luobo.mindustry.Mindustry;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mindustry.MODID);

    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("example_block", ModBlocks.EXAMPLE_BLOCK);

    public static final DeferredItem<Item> EXAMPLE_ITEM =
            ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new
            FoodProperties.Builder().alwaysEdible().nutrition(1).saturationModifier(2f).build())
    );
}
