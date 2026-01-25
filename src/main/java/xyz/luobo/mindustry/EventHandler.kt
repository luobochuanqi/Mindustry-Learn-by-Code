package xyz.luobo.mindustry

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.logging.LogUtils
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.util.Mth
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.IModBusEvent
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import org.slf4j.Logger
import software.bernie.geckolib.cache.`object`.GeoBone
import software.bernie.geckolib.renderer.GeoBlockRenderer
import xyz.luobo.mindustry.client.geoModels.DuoModel
import xyz.luobo.mindustry.common.ModBlockEntityTypes
import xyz.luobo.mindustry.common.machines.kiln.KilnBE
import xyz.luobo.mindustry.common.turrets.duo.DuoBE

object EventHandler {
    private val LOGGER: Logger = LogUtils.getLogger()

    // 此为客户端事件总线订阅器
    @EventBusSubscriber(modid = Mindustry.MOD_ID, value = [Dist.CLIENT])
    object ClientModEvents : IModBusEvent {
        @SubscribeEvent
        fun onClientSetup(event: FMLClientSetupEvent?) {
            LOGGER.info("HELLO FROM Client SETUP")
        }

        @SubscribeEvent
        fun registerEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
            event.registerBlockEntityRenderer(ModBlockEntityTypes.DUO_Block_Entity.get()) { DuoRenderer() }
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
            if (be is KilnBE) be.itemHandler else null
        }

        // 注册窑炉的能量存储 Capability
        event.registerBlockEntity(
            Capabilities.EnergyStorage.BLOCK,
            ModBlockEntityTypes.KILN_BLOCK_ENTITY.get()
        ) { be, _ ->
            if (be is KilnBE) be.energyStorage else null
        }
    }
}

class DuoRenderer :
    GeoBlockRenderer<DuoBE>(
//        DefaultedBlockGeoModel(ResourceLocation.fromNamespaceAndPath(Mindustry.MOD_ID, "duo"))
        DuoModel()
    ) {
    override fun renderRecursively(
        poseStack: PoseStack?,
        animatable: DuoBE?,
        bone: GeoBone?,
        renderType: RenderType?,
        bufferSource: MultiBufferSource?,
        buffer: VertexConsumer?,
        isReRender: Boolean,
        partialTick: Float,
        packedLight: Int,
        packedOverlay: Int,
        colour: Int
    ) {
        if (bone != null && bone.name.equals("turret")) {
            if (animatable != null) {
                // 获取当前Yaw并转换为弧度
                val yawRad = -(animatable.currentYaw * Mth.DEG_TO_RAD)
                bone.setRotY(yawRad)
            }
        }
        super.renderRecursively(
            poseStack,
            animatable,
            bone,
            renderType,
            bufferSource,
            buffer,
            isReRender,
            partialTick,
            packedLight,
            packedOverlay,
            colour
        )
    }
}