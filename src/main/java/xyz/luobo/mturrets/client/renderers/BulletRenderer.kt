package xyz.luobo.mturrets.client.renderers

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import xyz.luobo.mturrets.common.entity.bullet.BulletEntity

/**
 * 飞行弹渲染器(#31):面向相机的四边形,颜色/大小来自实体同步数据。
 * 用 entityTranslucentEmissive + FULL_BRIGHT:发射性材质不依赖环境光照,
 * 白天的天空与暗处地形下都醒目(legacy 的 RenderType.lightning 加色混合在明空下几乎不可见)。
 */
class BulletRenderer(context: EntityRendererProvider.Context) :
    EntityRenderer<BulletEntity>(context) {

    override fun getTextureLocation(entity: BulletEntity): ResourceLocation {
        // 未使用纹理,颜色直接写入顶点
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png")
    }

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
        val size = entity.bulletSize * 0.8f

        poseStack.pushPose()
        poseStack.translate(0.0, size / 2.0, 0.0)

        // 对齐相机(广告牌)
        val camera = Minecraft.getInstance().gameRenderer.mainCamera
        poseStack.mulPose(camera.rotation())

        poseStack.scale(size, size, size)

        val buffer = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(getTextureLocation(entity)))
        val mat = poseStack.last().pose()

        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f

        // NEW_ENTITY 格式按序写全:position/color/uv0/overlay/uv2(light)/normal
        buffer.addVertex(mat, -1f, -1f, 0f).setColor(r, g, b, 1f).setUv(0f, 1f)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 0f, 1f)
        buffer.addVertex(mat, -1f, 1f, 0f).setColor(r, g, b, 1f).setUv(0f, 0f)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 0f, 1f)
        buffer.addVertex(mat, 1f, 1f, 0f).setColor(r, g, b, 1f).setUv(1f, 0f)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 0f, 1f)
        buffer.addVertex(mat, 1f, -1f, 0f).setColor(r, g, b, 1f).setUv(1f, 1f)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 0f, 1f)

        poseStack.popPose()
    }
}