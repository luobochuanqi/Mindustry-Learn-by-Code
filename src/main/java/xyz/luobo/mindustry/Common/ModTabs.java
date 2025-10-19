package xyz.luobo.mindustry.Common;
//
//import net.minecraft.core.registries.Registries;
//import net.minecraft.network.chat.Component;
//import net.minecraft.world.item.CreativeModeTab;
//import net.minecraft.world.item.CreativeModeTabs;
//import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
//import net.neoforged.neoforge.registries.DeferredHolder;
//import net.neoforged.neoforge.registries.DeferredRegister;
//import xyz.luobo.mindustry.Mindustry;
//
//public class ModTabs {
//    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Mindustry.MOD_ID);
//
//    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB =
//            CREATIVE_MODE_TABS.register("example_tab",
//                    () -> CreativeModeTab.builder()
//                            .title(Component.translatable("itemGroup.mindustry"))
//                            .withTabsBefore(CreativeModeTabs.COMBAT)
//                            .icon(() -> ModItems.EXAMPLE_ITEM.get().getDefaultInstance())
//                            .displayItems((parameters, output) -> {output.accept(ModItems.EXAMPLE_ITEM.get());
//    }).build());
//
//    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
//        if (event.getTabKey() == EXAMPLE_TAB.getKey()) {
//            event.accept(ModItems.EXAMPLE_ITEM.get());
//        }
//    }
//}