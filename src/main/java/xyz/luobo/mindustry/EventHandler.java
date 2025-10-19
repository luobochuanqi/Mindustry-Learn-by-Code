package xyz.luobo.mindustry;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.slf4j.Logger;
import xyz.luobo.mindustry.Client.Renderers.PowerNodeBlockEntityRenderer;
import xyz.luobo.mindustry.Common.ModBlockEntities;

public class EventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    // 此为客户端事件总线订阅器
    @EventBusSubscriber(modid = Mindustry.MOD_ID, value = Dist.CLIENT)
    public static class ClientModEvents implements IModBusEvent {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("HELLO FROM Clent SETUP");
        }
        @SubscribeEvent // on the mod event bus only on the physical client
        public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(
                    // The block entity type to register the renderer for.
                    ModBlockEntities.INSTANCE.getPOWER_NODE_BLOCK_ENTITY().get(),
                    // A function of BlockEntityRendererProvider.Context to BlockEntityRenderer.
                    PowerNodeBlockEntityRenderer::new
            );
        }
    }

    // 此为服务端事件总线订阅器
    @EventBusSubscriber(modid = Mindustry.MOD_ID, value = Dist.DEDICATED_SERVER)
    public static class ServerModEvents implements IModBusEvent {
        @SubscribeEvent
        public static void onDedicatedServerSetup(FMLDedicatedServerSetupEvent event) {
            LOGGER.info("HELLO FROM Dedicated Server SETUP");
        }
    }
}
