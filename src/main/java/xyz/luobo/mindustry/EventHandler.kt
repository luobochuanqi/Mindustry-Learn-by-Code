package xyz.luobo.mindustry

import com.mojang.logging.LogUtils
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.IModBusEvent
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import org.slf4j.Logger
import xyz.luobo.mindustry.common.ModBlockEntityTypes
import xyz.luobo.mindustry.common.machines.kiln.KilnBE

object EventHandler {
    private val LOGGER: Logger = LogUtils.getLogger()

    // 此为客户端事件总线订阅器
    @EventBusSubscriber(modid = Mindustry.MOD_ID, value = [Dist.CLIENT])
    object ClientModEvents : IModBusEvent {
        @SubscribeEvent
        fun onClientSetup(event: FMLClientSetupEvent?) {
            LOGGER.info("HELLO FROM Client SETUP")
        }

//        @SubscribeEvent
//        fun registerEntityRenderers(event: RegisterRenderers) {
//            event.registerBlockEntityRenderer<PowerNodeBlockEntity>( // The block entity type to register the renderer for.
//                POWER_NODE_BLOCK_ENTITY.get(),  // A function of BlockEntityRendererProvider.Context to BlockEntityRenderer.
//                BlockEntityRendererProvider(::PowerNodeBlockEntityRenderer)
//            )
//        }
    }

    // 此为服务端事件总线订阅器
    @EventBusSubscriber(modid = Mindustry.MOD_ID, value = [Dist.DEDICATED_SERVER])
    object ServerModEvents : IModBusEvent {
        @SubscribeEvent
        fun onDedicatedServerSetup(event: FMLDedicatedServerSetupEvent?) {
            LOGGER.info("HELLO FROM Dedicated Server SETUP")
        }
    }

    fun registerCapabilities(event: RegisterCapabilitiesEvent) {
        // 注册窑炉的物品处理器 Capability
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            ModBlockEntityTypes.KILN_BLOCK_ENTITY.get(),
            { be, _ ->
                if (be is KilnBE) be.itemHandler else null
            }
        )

        // 注册窑炉的能量存储 Capability
        event.registerBlockEntity(
            Capabilities.EnergyStorage.BLOCK,
            ModBlockEntityTypes.KILN_BLOCK_ENTITY.get(),
            { be, _ ->
                if (be is KilnBE) be.energyStorage else null
            }
        )
    }
}