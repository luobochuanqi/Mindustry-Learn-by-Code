package xyz.luobo.mindustry

import com.mojang.logging.LogUtils
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.IModBusEvent
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent
import org.slf4j.Logger
import xyz.luobo.mindustry.common.ModBlockEntityTypes
import xyz.luobo.mindustry.common.ModFluids
import xyz.luobo.mindustry.common.machines.kiln.KilnBE

object EventHandler {
    private val LOGGER: Logger = LogUtils.getLogger()

    // 原版水纹理
    private val WATER_ORIGIN_STILL: ResourceLocation = ResourceLocation.withDefaultNamespace("block/water_still")

    // 此为客户端事件总线订阅器
    @EventBusSubscriber(modid = Mindustry.MOD_ID, value = [Dist.CLIENT])
    object ClientModEvents : IModBusEvent {
        @SubscribeEvent
        fun onClientSetup(event: FMLClientSetupEvent?) {
            LOGGER.info("HELLO FROM Client SETUP")
        }

        @SubscribeEvent
        fun registerEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
            // TODO: 注册新的炮台渲染器
            // event.registerBlockEntityRenderer(ModBlockEntityTypes.DUO_BLOCK_ENTITY.get()) { DuoTurretRenderer() }
        }

        @SubscribeEvent
        fun registerClientExtensions(event: RegisterClientExtensionsEvent) {
            event.registerFluidType(object : IClientFluidTypeExtensions {
                override fun getTintColor(): Int {
                    return 0xFF1C274E.toInt()
                }

                override fun getFlowingTexture(): ResourceLocation {
                    return WATER_ORIGIN_STILL
                }

                override fun getStillTexture(): ResourceLocation {
                    return WATER_ORIGIN_STILL
                }
            }, ModFluids.WATER_TYPE.get())
        }
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
            ModBlockEntityTypes.KILN_BLOCK_ENTITY.get()
        ) { be, _ ->
            if (be is KilnBE) be.itemCapability else null
        }

        // 注册窑炉的能量存储 Capability
        event.registerBlockEntity(
            Capabilities.EnergyStorage.BLOCK,
            ModBlockEntityTypes.KILN_BLOCK_ENTITY.get()
        ) { be, _ ->
            if (be is KilnBE) be.energyCapability else null
        }

        // TODO: 注册新的炮台 Capability
        // 注册 Duo 炮台的物品处理器
        // event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntityTypes.DUO_BLOCK_ENTITY.get()) { be, _ ->
        //     if (be is DuoTurretBlockEntity) be.itemHandler else null
        // }

        // 注册 Arc 和 Meltdown 炮台的能量存储
        // event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntityTypes.ARC_BLOCK_ENTITY.get()) { be, _ ->
        //     if (be is ArcTurretBlockEntity) be.energyStorage else null
        // }
    }
}