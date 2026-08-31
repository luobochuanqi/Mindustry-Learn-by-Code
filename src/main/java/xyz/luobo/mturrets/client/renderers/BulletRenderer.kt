package xyz.luobo.mturrets.client.renderers

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f
import org.joml.Vector3f
import xyz.luobo.mturrets.MTurrets
import xyz.luobo.mturrets.common.entity.bullet.BulletEntity

/**
 * 飞行弹渲染器(#31/#53):双层贴图(深色八角背衬 + 弹壳),同尺寸绘制(1.0×,忠实上游);
 * 绕视线轴滚转把 quad 上方向对齐速度在屏幕上的投影;寿命末 25% 线性收缩至 0。
 * 发射性材质 + FULL_BRIGHT:不依赖环境光照,白天天空与暗处地形下都醒目。
 * 零新增同步字段:颜色顶字节打包 lifetime(ADR-0010),朝向走原版 yRot/xRot 同步。
 */
class BulletRenderer(context: EntityRendererProvider.Context) :
    EntityRenderer<BulletEntity>(context) {

    override fun getTextureLocation(entity: BulletEntity): ResourceLocation = FRONT_TEXTURE

    override fun render(
        entity: BulletEntity,
        entityYaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int
    ) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight)

        val color = entity.bulletColor
        // 顶字节 = lifetime(ADR-0010);fout 由客户端自身 tickCount 推导,网络延迟只偏移几 tick
        val lifetime = (color ushr 24) and 0xFF
        val fout = if (lifetime <= 0) 1f else 1f - entity.tickCount / lifetime.toFloat()
        // 前 75% 满尺寸,末 25% 线性收至 0(fout = 剩余生命比例,满尺寸直到剩 25%)
        val shrink = (fout / 0.25f).coerceIn(0f, 1f)
        val size = entity.bulletSize * 0.8f * shrink

        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f

        poseStack.pushPose()
        poseStack.translate(0.0, size / 2.0, 0.0)

        // 对齐相机(广告牌)
        val camera = Minecraft.getInstance().gameRenderer.mainCamera
        poseStack.mulPose(camera.rotation())
        // 绕视线轴滚转:quad 上方向对齐速度的屏幕投影(方向由服务端 yRot/xRot 携带,零新增同步)
        poseStack.mulPose(Axis.ZP.rotationDegrees(viewRoll(entity, camera)))
        poseStack.scale(size, size, size)

        val mat = poseStack.last().pose()
        // 背衬层:暗化 0.5×;弹壳层:弹色(背衬先画,被弹壳覆盖)
        addQuad(bufferSource.getBuffer(RenderType.entityTranslucentEmissive(BACK_TEXTURE)), mat, r * 0.5f, g * 0.5f, b * 0.5f)
        addQuad(bufferSource.getBuffer(RenderType.entityTranslucentEmissive(FRONT_TEXTURE)), mat, r, g, b)

        poseStack.popPose()
    }

    /** 视线轴滚转角:把世界方向投影到相机平面,quad 上方向(+Y)转到该投影方向。 */
    private fun viewRoll(entity: BulletEntity, camera: Camera): Float {
        val yaw = Math.toRadians(entity.yRot.toDouble())
        val pitch = Math.toRadians(entity.xRot.toDouble())
        val dir = Vector3f(
            (Math.sin(yaw) * Math.cos(pitch)).toFloat(),
            Math.sin(pitch).toFloat(),
            (Math.cos(yaw) * Math.cos(pitch)).toFloat()
        )
        // 世界 → 视空间(与 poseStack 的 mulPose(camera.rotation()) 同一变换);(x, y) 即屏幕投影
        camera.rotation().transform(dir)
        return Math.toDegrees(Math.atan2(-dir.x.toDouble(), dir.y.toDouble())).toFloat()
    }

    /** NEW_ENTITY 格式按序写全:position/color/uv0/overlay/uv2(light)/normal。贴图长轴=文件竖直=quad 上方向。 */
    private fun addQuad(buffer: VertexConsumer, mat: Matrix4f, r: Float, g: Float, b: Float) {
        buffer.addVertex(mat, -1f, -1f, 0f).setColor(r, g, b, 1f).setUv(0f, 1f)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 0f, 1f)
        buffer.addVertex(mat, -1f, 1f, 0f).setColor(r, g, b, 1f).setUv(0f, 0f)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 0f, 1f)
        buffer.addVertex(mat, 1f, 1f, 0f).setColor(r, g, b, 1f).setUv(1f, 0f)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 0f, 1f)
        buffer.addVertex(mat, 1f, -1f, 0f).setColor(r, g, b, 1f).setUv(1f, 1f)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 0f, 1f)
    }

    companion object {
        /** 一对贴图全弹种共用(ADR-0010):背衬(八角)在下,弹壳(六角)在上,同尺寸绘制。 */
        private val FRONT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MTurrets.MOD_ID, "textures/entity/bullet.png")
        private val BACK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MTurrets.MOD_ID, "textures/entity/bullet-back.png")
    }
}
