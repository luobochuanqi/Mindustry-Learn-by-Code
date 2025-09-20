package xyz.luobo.mindustry;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import org.slf4j.Logger;

public class EventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    // 此为客户端事件总线订阅器
    @EventBusSubscriber(modid = Mindustry.MODID, value = Dist.CLIENT)
    public static class ClientModEvents implements IModBusEvent {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("HELLO FROM Clent SETUP");
        }
    }

    // 此为服务端事件总线订阅器
    @EventBusSubscriber(modid = Mindustry.MODID, value = Dist.DEDICATED_SERVER)
    public static class ServerModEvents implements IModBusEvent {
        @SubscribeEvent
        public static void onDedicatedServerSetup(FMLDedicatedServerSetupEvent event) {
            LOGGER.info("HELLO FROM Dedicated Server SETUP");
        }
    }
}
