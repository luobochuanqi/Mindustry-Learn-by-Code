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
 * 飞行弹渲染器(#31/#53/#70):双层贴图(深色八角背衬 + 弹壳,短侧朝速度)+ 加色泛光(eyes 通道,
 * 画在弹壳之后盖于其上:外层弹色光晕 + 内层白热核心,加色饱和把中心冲成白热,龙眼同款做法)。
 * 光晕径向贴图把衰减烘焙进 RGB、alpha 恒 255(加色 ONE,ONE 下黑即无贡献,同时避开 eyes 着色器
 * alpha<0.1 discard)。弹体尺寸经 SHELL_CANVAS_SCALE 补偿贴图留白;寿命末 25% 线性收缩至 0。
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
        // 弹体 quad 半幅:bulletSize × 画布补偿 × 收缩
        val size = entity.bulletSize * SHELL_CANVAS_SCALE * shrink

        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f

        poseStack.pushPose()
        // 渲染原点 = 实体脚底;抬到碰撞箱中心,弹体与光晕同心(与尺寸无关,免随缩放漂移)
        poseStack.translate(0.0, entity.bbHeight / 2.0, 0.0)
        // 对齐相机(广告牌)
        val camera = Minecraft.getInstance().gameRenderer.mainCamera
        poseStack.mulPose(camera.rotation())
        // 绕视线轴滚转:+90° 使贴图长轴(文件竖直)垂直速度屏幕投影,即短侧朝移动方向
        poseStack.mulPose(Axis.ZP.rotationDegrees(viewRoll(entity, camera) + 90f))

        // 弹壳:背衬(暗 0.5×)先画,弹壳(弹色)覆盖其上
        poseStack.pushPose()
        poseStack.scale(size, size, size)
        val mat = poseStack.last().pose()
        addQuad(bufferSource.getBuffer(RenderType.entityTranslucentEmissive(BACK_TEXTURE)), mat, r * 0.5f, g * 0.5f, b * 0.5f)
        addQuad(bufferSource.getBuffer(RenderType.entityTranslucentEmissive(FRONT_TEXTURE)), mat, r, g, b)
        poseStack.popPose()

        // 泛光(加色,盖在弹壳上):外层弹色光晕,内层白核心把中心冲白
        // NOTE: 沿视线向相机方向偏移 0.02 格再画——共面 quad 在原版靠 LEQUAL 平深度可通过,
        // 但 Sodium 按批次/距离重绘 translucent 与 eyes 层,共面会被弹壳深度剔除;偏移后严格更近,两端一致。
        poseStack.pushPose()
        poseStack.translate(0.0, 0.0, 0.02)
        val halo = size * GLOW_SCALE
        poseStack.scale(halo, halo, halo)
        addQuad(bufferSource.getBuffer(RenderType.eyes(GLOW_TEXTURE)), poseStack.last().pose(), r, g, b)
        poseStack.popPose()
        poseStack.pushPose()
        poseStack.translate(0.0, 0.0, 0.02)
        val core = size * CORE_SCALE
        poseStack.scale(core, core, core)
        addQuad(bufferSource.getBuffer(RenderType.eyes(GLOW_TEXTURE)), poseStack.last().pose(), 1f, 1f, 1f)
        poseStack.popPose()

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

    /** NEW_ENTITY 格式按序写全:position/color/uv0/overlay/uv2(light)/normal。贴图长轴=文件竖直=quad 上方向(滚转后垂直速度)。 */
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
        /** 泛光贴图(#70):64px 径向白→黑,衰减烘焙进 RGB、alpha 恒 255;全弹种共用,tint 分色。 */
        private val GLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MTurrets.MOD_ID, "textures/entity/bullet-glow.png")
        /** 弹体画布补偿系数:贴图长轴占 52px 画布 28px;铜弹 bulletSize=0.5 → 可见长轴 ≈0.65 格。 */
        private const val SHELL_CANVAS_SCALE = 1.2f
        /** 光晕画布 / 弹体画布:弹色外溢晕。 */
        private const val GLOW_SCALE = 1.5f
        /** 白热核心画布 / 弹体画布:加色饱和区。 */
        private const val CORE_SCALE = 0.6f
    }
}
