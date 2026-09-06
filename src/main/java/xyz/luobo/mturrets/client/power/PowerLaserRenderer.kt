package xyz.luobo.mturrets.client.power

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import xyz.luobo.mturrets.core.power.PowerMemberBE
import xyz.luobo.mturrets.common.power.PowerNodeBE
import java.util.OptionalDouble

/**
 * 电力节点激光渲染(ADR-0007 修订 #69,对位 Mindustry `drawLaser`):
 * 以本节点为源,向每个已同步的链路端点画一条静态激光,颜色随本图供电比例
 * [PowerNodeBE.supplyRatioLaser] 在供饱白 / 棕停金 `#fbd367` 间渐变(Mindustry 健康色)。
 *
 * [PowerNodeRenderer] 让节点 BE 进入 `SectionCompiler` 的 renderableBlockEntities
 * (TurretDebug #77 同款结论:无 BER 的 BE 不可被 `iterateVisibleBlockEntities` 看见),
 * 本对象在 RenderLevelStageEvent 实际画线。
 */
class PowerNodeRenderer(context: BlockEntityRendererProvider.Context) : BlockEntityRenderer<PowerNodeBE> {
    override fun render(
        blockEntity: PowerNodeBE,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: net.minecraft.client.renderer.MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
    }
}

/**
 * 电力激光的等级渲染(ADR-0007 修订 #69):视野内每台在役节点沿 [PowerNodeBE.links] 画静态激光;
 * 颜色 = 供饱白 lerp 棕停金 `#fbd367`(系数 `1 - satisfaction`),与 Mindustry `setupColor` 一致。
 */
@EventBusSubscriber(modid = xyz.luobo.mturrets.MTurrets.MOD_ID, value = [Dist.CLIENT])
object PowerLaserRenderer {
    const val AMBER = 0xFBD367.toInt()

    private val LASER_LINE: RenderType = RenderType.create(
        "mturrets_power_laser",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.LINES,
        4096,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
            .setLineState(RenderStateShard.LineStateShard(OptionalDouble.of(2.0)))
            .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
            .setCullState(RenderStateShard.NO_CULL)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false)
    )

    @SubscribeEvent
    fun onRenderLevelStage(event: net.neoforged.neoforge.client.event.RenderLevelStageEvent) {
        if (event.stage != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val camera = event.camera.position

        val buffer = mc.renderBuffers().bufferSource().getBuffer(LASER_LINE)
        event.poseStack.pushPose()
        for (be in PowerLinkSelector.allNodes()) {
            if (be.isRemoved || be.level !== level) continue
            drawNode(be, camera, buffer)
        }
        event.poseStack.popPose()
    }

    private fun drawNode(node: PowerMemberBE, camera: Vec3, buffer: VertexConsumer) {
        val satisfaction = (node as? PowerNodeBE)?.supplyRatioLaser ?: 1f
        // 供饱白 / 棕停金 Mth.lerp(1-satisfaction) 混合
        val white = 1.0f
        val amberR = ((AMBER shr 16) and 0xFF) / 255f
        val amberG = ((AMBER shr 8) and 0xFF) / 255f
        val amberB = (AMBER and 0xFF) / 255f
        val t = (1f - satisfaction).coerceIn(0f, 1f)
        val r = Mth.lerp(t, white, amberR)
        val g = Mth.lerp(t, white, amberG)
        val b = Mth.lerp(t, white, amberB)

        val src = node.blockPos.center.add(0.0, 0.5, 0.0)
        for (link in node.links) {
            val dst = link.center.add(0.0, 0.5, 0.0)
            addLine(buffer, src.subtract(camera), dst.subtract(camera), r, g, b, 1f)
        }
    }

    private fun addLine(buffer: VertexConsumer, from: Vec3, to: Vec3, r: Float, g: Float, b: Float, a: Float) {
        buffer.addVertex(from.x.toFloat(), from.y.toFloat(), from.z.toFloat()).setColor(r, g, b, a)
        buffer.addVertex(to.x.toFloat(), to.y.toFloat(), to.z.toFloat()).setColor(r, g, b, a)
    }
}