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
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import org.slf4j.Logger
import xyz.luobo.mturrets.client.fx.ModFxClient
import xyz.luobo.mturrets.common.BulletFxPayload
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.common.ModFluids
import xyz.luobo.mturrets.common.liquids.FluidRegistry
import xyz.luobo.mturrets.common.liquids.Liquids
import xyz.luobo.mturrets.common.ModBlocks
import xyz.luobo.mturrets.common.machines.kiln.KilnBE
import xyz.luobo.mturrets.common.machines.drill.DrillBE
import xyz.luobo.mturrets.core.structure.BlueprintAnchor
import xyz.luobo.mturrets.core.structure.StructuralBlock

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
            // 预注册 partial 模型:必须在首次资源重载烘焙之前(懒加载会错过 RegisterAdditional)
            xyz.luobo.mturrets.client.visual.DuoModels.preload()
            xyz.luobo.mturrets.client.visual.ScatterModels.preload()
            // Duo 动件 visual(ADR-0002/0005):Flywheel GPU 轨接管;visual 缺席时退回静态方块模型
            dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer.builder(
                xyz.luobo.mturrets.common.ModBlockEntityTypes.DUO_BLOCK_ENTITY.get()
            )
                .factory { ctx, be, pt ->
                    xyz.luobo.mturrets.client.visual.DuoVisual(ctx, be, pt)
                }
                .apply()
            // Scatter 动件 visual(#34):旋转头 + -mid 后坐中段
            dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer.builder(
                xyz.luobo.mturrets.common.ModBlockEntityTypes.SCATTER_BLOCK_ENTITY.get()
            )
                .factory { ctx, be, pt ->
                    xyz.luobo.mturrets.client.visual.ScatterVisual(ctx, be, pt)
                }
                .apply()
        }

        @SubscribeEvent
        fun registerEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
            // 注册子弹实体渲染器
            event.registerEntityRenderer(
                xyz.luobo.mturrets.common.ModEntities.TURRET_BULLET.get()
            ) { context ->
                xyz.luobo.mturrets.client.renderers.BulletRenderer(context)
            }
        }

        @SubscribeEvent
        fun registerParticleProviders(event: RegisterParticleProvidersEvent) {
            // 子弹 FX 粒子工厂(#62):引擎按类型名自动建 SpriteSet,工厂只粒子化
            xyz.luobo.mturrets.client.fx.ModFxClient.registerParticleProviders(event)
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

    // payload 编码/解码两端都要(client handler 跑客户端,服务端 encode 走同 codec)→ Dist.ANY。
    // playToClient 的 handler 只在客户端收到时触发(专用服务端只登记 codec、不跑 handler);
    // handler 在 network 线程到达,粒子必须 main thread 放 → enqueueWork。
    @EventBusSubscriber(modid = MTurrets.MOD_ID, value = [Dist.CLIENT, Dist.DEDICATED_SERVER])
    object PayloadEvents : IModBusEvent {
        @SubscribeEvent
        fun registerPayloads(event: RegisterPayloadHandlersEvent) {
            event.registrar("1").playToClient(
                xyz.luobo.mturrets.common.BulletFxPayload.TYPE,
                xyz.luobo.mturrets.common.BulletFxPayload.STREAM_CODEC
            ) { payload, ctx ->
                ctx.enqueueWork {
                    val level =
                        (ctx.player().level() as? net.minecraft.client.multiplayer.ClientLevel) ?: return@enqueueWork
                    xyz.luobo.mturrets.client.fx.ModFxClient.dispatch(level, payload)
                }
            }
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
        // 窑炉(#33):Buffer / 内罐 / 储能三通道(1×1 蓝图锚点,查询即锚点位)
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            ModBlockEntityTypes.KILN.get()
        ) { be, _ -> (be as? KilnBE)?.itemCapability }

        event.registerBlockEntity(
            Capabilities.EnergyStorage.BLOCK,
            ModBlockEntityTypes.KILN.get()
        ) { be, _ -> (be as? KilnBE)?.energyCapability }

        event.registerBlockEntity(
            Capabilities.FluidHandler.BLOCK,
            ModBlockEntityTypes.KILN.get()
        ) { be, _ -> (be as? KilnBE)?.fluidCapability }

        // 燃烧发电机(#56):燃料槽(仅煤,总量 8);无能量/液体通道
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            ModBlockEntityTypes.COMBUSTION_GENERATOR.get()
        ) { be, _ -> (be as? xyz.luobo.mturrets.common.power.CombustionGeneratorBE)?.itemCapability }

        // 钻头(#35):Buffer / 内罐(水加成)两通道(2×2 蓝图锚点,查询即锚点位)
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            ModBlockEntityTypes.DRILL.get()
        ) { be, _ -> (be as? DrillBE)?.itemCapability }

        event.registerBlockEntity(
            Capabilities.FluidHandler.BLOCK,
            ModBlockEntityTypes.DRILL.get()
        ) { be, _ -> (be as? DrillBE)?.fluidCapability }

        // Duo 炮台(#31):Magazine 为单位账——供弹能力槽面把标准 IItemHandler 翻译到单位账(issue 73)。
        // 只暴露 Coolant 内罐 + 弹药注入口(成员格 block 级路由见下方注册)
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            ModBlockEntityTypes.DUO_BLOCK_ENTITY.get()
        ) { be, _ ->
            if (be is xyz.luobo.mturrets.common.turrets.DuoTurretBE) be.magazineHandler else null
        }

        event.registerBlockEntity(
            Capabilities.FluidHandler.BLOCK,
            ModBlockEntityTypes.DUO_BLOCK_ENTITY.get()
        ) { be, _ ->
            if (be is xyz.luobo.mturrets.common.turrets.DuoTurretBE) be.fluidCapability else null
        }

        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            ModBlockEntityTypes.SCATTER_BLOCK_ENTITY.get()
        ) { be, _ ->
            if (be is xyz.luobo.mturrets.common.turrets.ScatterTurretBE) be.magazineHandler else null
        }

        event.registerBlockEntity(
            Capabilities.FluidHandler.BLOCK,
            ModBlockEntityTypes.SCATTER_BLOCK_ENTITY.get()
        ) { be, _ ->
            if (be is xyz.luobo.mturrets.common.turrets.ScatterTurretBE) be.fluidCapability else null
        }

        // 电池(#30):对外充放 capability(各限 200 FE/次);节点零储能不注册能力,
        // 节点读不到储能——电能只住在电池与耗电结构本地缓冲
        event.registerBlockEntity(
            Capabilities.EnergyStorage.BLOCK,
            ModBlockEntityTypes.BATTERY.get()
        ) { be, _ ->
            if (be is xyz.luobo.mturrets.common.power.BatteryBE) be.energyCapability else null
        }

        // 钻头/脚手架成员格物品能力路由回锚点 Buffer(#35);Scatter 成员格弹药路由见下方专用注册。
        event.registerBlock(
            Capabilities.ItemHandler.BLOCK,
            { level, pos, state, _, _ ->
                val anchorPos = pos.subtract(StructuralBlock.decodeOffset(state))
                (level.getBlockEntity(anchorPos) as? BlueprintAnchor)?.itemCapability
            },
            ModBlocks.TEST_STRUCTURAL.get(),
            ModBlocks.DRILL_STRUCTURAL.get()
        )

        // Scatter(#34/#73):成员格流体/弹药路由回锚点(Coolant 内罐 + 供弹能力槽面,装水/供弹经成员面可插任意成员格)
        event.registerBlock(
            Capabilities.ItemHandler.BLOCK,
            { level, pos, state, _, _ ->
                val anchorPos = pos.subtract(StructuralBlock.decodeOffset(state))
                (level.getBlockEntity(anchorPos) as? xyz.luobo.mturrets.common.turrets.ScatterTurretBE)?.magazineHandler
            },
            ModBlocks.SCATTER_STRUCTURAL.get()
        )

        event.registerBlock(
            Capabilities.FluidHandler.BLOCK,
            { level, pos, state, _, _ ->
                val anchorPos = pos.subtract(StructuralBlock.decodeOffset(state))
                (level.getBlockEntity(anchorPos) as? xyz.luobo.mturrets.common.turrets.ScatterTurretBE)?.fluidCapability
            },
            ModBlocks.SCATTER_STRUCTURAL.get()
        )
    }
}
