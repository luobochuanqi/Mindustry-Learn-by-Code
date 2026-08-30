package xyz.luobo.mturrets.client.renderers

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation
import xyz.luobo.mturrets.common.entity.bullet.BulletEntity

/**
 * 飞行弹渲染器(#31):面向相机的发光四边形,颜色与大小来自实体同步数据
 * (服务端权威运动,客户端不模拟)。与 legacy 渲染同构,换绑新 [BulletEntity]。
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
        val size = entity.bulletSize * 0.35f

        poseStack.pushPose()
        poseStack.translate(0.0, size / 2.0, 0.0)

        // 对齐相机(广告牌)
        val camera = Minecraft.getInstance().gameRenderer.mainCamera
        poseStack.mulPose(camera.rotation())

        poseStack.scale(size, size, size)

        val buffer = bufferSource.getBuffer(RenderType.lightning())
        val mat = poseStack.last().pose()

        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f

        buffer.addVertex(mat, -1f, -1f, 0f).setColor(r, g, b, 1f)
        buffer.addVertex(mat, -1f, 1f, 0f).setColor(r, g, b, 1f)
        buffer.addVertex(mat, 1f, 1f, 0f).setColor(r, g, b, 1f)
        buffer.addVertex(mat, 1f, -1f, 0f).setColor(r, g, b, 1f)

        poseStack.popPose()
    }
}