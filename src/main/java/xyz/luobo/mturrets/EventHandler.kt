package xyz.luobo.mturrets

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
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.common.ModFluids
import xyz.luobo.mturrets.common.liquids.FluidRegistry
import xyz.luobo.mturrets.common.liquids.Liquids
import xyz.luobo.mturrets.common.machines.kiln.KilnBE

object EventHandler {
    private val LOGGER: Logger = LogUtils.getLogger()

    // 原版水纹理（作为默认纹理）
    private val WATER_STILL: ResourceLocation = ResourceLocation.withDefaultNamespace("block/water_still")
    private val WATER_FLOW: ResourceLocation = ResourceLocation.withDefaultNamespace("block/water_flow")

    // 此为客户端事件总线订阅器
    @EventBusSubscriber(modid = MTurrets.MOD_ID, value = [Dist.CLIENT])
    object ClientModEvents : IModBusEvent {
        @SubscribeEvent
        fun onClientSetup(event: FMLClientSetupEvent?) {
        }

        @SubscribeEvent
        fun registerEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
            // 注册 Duo 炮台渲染器
            event.registerBlockEntityRenderer(
                ModBlockEntityTypes.DUO_BLOCK_ENTITY.get()
            ) { context ->
                xyz.luobo.mturrets.client.renderers.DuoRenderer(context)
            }

            // 注册子弹实体渲染器
            event.registerEntityRenderer(
                xyz.luobo.mturrets.common.ModEntities.TURRET_BULLET.get()
            ) { context ->
                xyz.luobo.mturrets.client.renderers.TurretBulletRenderer(context)
            }
        }

        @SubscribeEvent
        fun registerClientExtensions(event: RegisterClientExtensionsEvent) {
            // 自动注册所有液体的客户端扩展
            registerAllFluidClientExtensions(event)
        }

        /**
         * 自动为所有液体注册客户端扩展
         * 避免手动为每个液体写重复代码
         */
        private fun registerAllFluidClientExtensions(event: RegisterClientExtensionsEvent) {
            // 遍历所有液体类型，自动注册
            Liquids.ALL.forEach { liquid ->
                val registry = ModFluids[liquid]
                registerFluidClientExtension(event, registry)
            }

            LOGGER.info("Registered ${Liquids.ALL.size} fluid client extensions")
        }

        /**
         * 为单个液体注册客户端扩展
         */
        private fun registerFluidClientExtension(
            event: RegisterClientExtensionsEvent,
            registry: FluidRegistry
        ) {
            event.registerFluidType(object : IClientFluidTypeExtensions {
                override fun getTintColor(): Int {
                    return registry.color
                }

                override fun getFlowingTexture(): ResourceLocation {
                    return registry.flowingTexture
                }

                override fun getStillTexture(): ResourceLocation {
                    return registry.stillTexture
                }
            }, registry.fluidType.get())
        }
    }

    // 此为服务端事件总线订阅器
    @EventBusSubscriber(modid = MTurrets.MOD_ID, value = [Dist.DEDICATED_SERVER])
    object ServerModEvents : IModBusEvent {
        @SubscribeEvent
        fun onDedicatedServerSetup(event: FMLDedicatedServerSetupEvent?) {
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

        // 注册 Duo 炮台的物品弹药 Capability
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            ModBlockEntityTypes.DUO_BLOCK_ENTITY.get()
        ) { be, _ ->
            if (be is xyz.luobo.mturrets.common.turrets.DuoTurretBlockEntity) be.itemCapability else null
        }

        // 注册电力节点的能量存储 Capability(网络传输与外部注入/抽取入口)
        event.registerBlockEntity(
            Capabilities.EnergyStorage.BLOCK,
            ModBlockEntityTypes.POWER_NODE_BLOCK_ENTITY.get()
        ) { be, _ ->
            if (be is xyz.luobo.mturrets.common.blockEntities.PowerNodeBlockEntity) be.energyCapability else null
        }

        // 注册能量炮台(Arc/Meltdown)的能量存储 Capability
        event.registerBlockEntity(
            Capabilities.EnergyStorage.BLOCK,
            ModBlockEntityTypes.ARC_BLOCK_ENTITY.get()
        ) { be, _ ->
            if (be is xyz.luobo.mturrets.common.turrets.ArcTurretBlockEntity) be.energyCapability else null
        }
        event.registerBlockEntity(
            Capabilities.EnergyStorage.BLOCK,
            ModBlockEntityTypes.MELTDOWN_BLOCK_ENTITY.get()
        ) { be, _ ->
            if (be is xyz.luobo.mturrets.common.turrets.MeltdownTurretBlockEntity) be.energyCapability else null
        }
    }
}
