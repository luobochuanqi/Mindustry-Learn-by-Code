package xyz.luobo.mindustry;
//
//import com.mojang.logging.LogUtils;
//import net.neoforged.bus.api.IEventBus;
//import net.neoforged.fml.ModContainer;
//import net.neoforged.fml.common.Mod;
//import net.neoforged.fml.config.ModConfig;
//import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
//import org.slf4j.Logger;
//import xyz.luobo.mindustry.Common.ModBlocks;
//import xyz.luobo.mindustry.Common.ModItems;
//import xyz.luobo.mindustry.Common.ModTabs;
//
//@Mod(Mindustry.MODID)
//public class Mindustry {
//    public static final String MODID = "mindustry";
//    private static final Logger LOGGER = LogUtils.getLogger();
//
//    public Mindustry(IEventBus modEventBus, ModContainer modContainer) {
//        modEventBus.addListener(this::commonSetup);
//
//        ModBlocks.register();
//        ModItems.ITEMS.register(modEventBus);
//        ModTabs.CREATIVE_MODE_TABS.register(modEventBus);
//
////        NeoForge.EVENT_BUS.register(this);
//
//        modEventBus.addListener(ModTabs::addCreative);
//
//        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
//    }
//
//    private void commonSetup(final FMLCommonSetupEvent event) {
//        LOGGER.info("HELLO FROM COMMON SETUP");
//    }
//}
