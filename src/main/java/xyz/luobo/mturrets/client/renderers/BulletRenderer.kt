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
 * 飞行弹渲染器(#31/#53/#70):形状光晕(弹体剪影高斯模糊,画在弹体**后**)+ 双层贴图
 * (暗化背衬出描边 + 弹壳提白 55% 作白热核心,短侧朝速度);寿命末 25% 线性收缩至 0。
 * 光晕贴图衰减烘焙进 RGB、alpha 恒 255(加色 ONE,ONE 下黑即无贡献,同时避开 eyes 着色器
 * alpha<0.1 discard);圆形径向会把弹体糊成灯泡,形状光晕才保轮廓。
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
        val halo = size * GLOW_SCALE
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

        // 光晕(加色,弹体后):剪影模糊贴图随弹色 tint;沿视线远离相机退 0.02 格——
        // eyes 通道不写深度,弹体先画时其深度把光晕中心裁掉、只留外缘;Sodium 重排批次后同理。
        poseStack.pushPose()
        poseStack.translate(0.0, 0.0, -0.02)
        poseStack.scale(halo, halo, halo)
        addQuad(bufferSource.getBuffer(RenderType.eyes(GLOW_TEXTURE)), poseStack.last().pose(), r, g, b)
        poseStack.popPose()

        // 弹壳:背衬(暗 0.5×)描边,弹壳提白 55% 作白热核心(形状保留,不盖白团)
        poseStack.pushPose()
        poseStack.scale(size, size, size)
        val mat = poseStack.last().pose()
        addQuad(bufferSource.getBuffer(RenderType.entityTranslucentEmissive(BACK_TEXTURE)), mat, r * 0.5f, g * 0.5f, b * 0.5f)
        val w = CORE_WHITE
        addQuad(
            bufferSource.getBuffer(RenderType.entityTranslucentEmissive(FRONT_TEXTURE)), mat,
            r + (1f - r) * w, g + (1f - g) * w, b + (1f - b) * w
        )
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
        /** 形状光晕贴图(#70):弹壳剪影高斯模糊,衰减烘焙进 RGB、alpha 恒 255;tint 分色。 */
        private val GLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MTurrets.MOD_ID, "textures/entity/bullet-glow.png")
        /** 弹体画布补偿系数:贴图长轴占 52px 画布 28px;铜弹 bulletSize=0.5 → 可见长轴 ≈0.49 格。 */
        private const val SHELL_CANVAS_SCALE = 0.9f
        /** 光晕画布 / 弹体画布:形状外溢晕。 */
        private const val GLOW_SCALE = 1.6f
        /** 弹壳向白插值的比例:白热核心来自提亮弹体本身,而非覆盖光团。 */
        private const val CORE_WHITE = 0.55f
    }
}
