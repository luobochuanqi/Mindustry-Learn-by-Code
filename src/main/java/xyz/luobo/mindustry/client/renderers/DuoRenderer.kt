package xyz.luobo.mindustry.client.renderers

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.GeoBlockRenderer
import xyz.luobo.mindustry.Mindustry
import xyz.luobo.mindustry.common.turrets.DuoTurretBlockEntity

/**
 * Duo 炮台渲染器
 * 使用 Geckolib 4 代码驱动动画，实现实时瞄准
 *
 * 模型骨骼结构：
 * - turret: 控制水平旋转 (Yaw)
 * - up: 控制垂直俯仰 (Pitch)
 */
class DuoRenderer(context: BlockEntityRendererProvider.Context) :
    GeoBlockRenderer<DuoTurretBlockEntity>(DuoModel()) {

    /**
     * 重写 render 方法以正确处理光照
     * 使用 LevelRenderer.getLightColor 获取方块位置的完整光照信息
     */
    override fun render(
        animatable: DuoTurretBlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        // 重新计算方块位置的光照值
        // 这确保了即使在阴影中也能正确显示
        val blockPos = animatable.blockPos
        val level = animatable.level

        val lightColor = if (level != null) {
            net.minecraft.client.renderer.LevelRenderer.getLightColor(level, blockPos)
        } else {
            packedLight
        }

        super.render(animatable, partialTick, poseStack, bufferSource, lightColor, packedOverlay)
    }

    override fun preRender(
        poseStack: PoseStack,
        animatable: DuoTurretBlockEntity,
        model: software.bernie.geckolib.cache.`object`.BakedGeoModel,
        bufferSource: MultiBufferSource?,
        buffer: VertexConsumer?,
        isReRender: Boolean,
        partialTick: Float,
        packedLight: Int,
        packedOverlay: Int,
        colour: Int
    ) {
        // 在渲染前设置骨骼旋转
        // turret 骨骼控制水平旋转 (Yaw)
        val turretBone = model.getBone("turret").orElse(null)
        // up 骨骼控制垂直俯仰 (Pitch)
        val upBone = model.getBone("up").orElse(null)

        if (turretBone != null) {
            // 设置 Y 轴旋转 (水平偏转 Yaw)
            // 减去模型的初始180度旋转，使炮口朝向正确的方向
            val yawRad = Math.toRadians((-animatable.currentRotation).toDouble()).toFloat()
            turretBone.setRotY(yawRad)
        }

        if (upBone != null) {
            // 设置 X 轴旋转 (垂直俯仰 Pitch)
            // 反转方向：目标向下时炮台也向下
            val pitchRad = Math.toRadians(animatable.currentPitch.toDouble()).toFloat()
            upBone.setRotX(pitchRad)
        }

        super.preRender(
            poseStack,
            animatable,
            model,
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

/**
 * Duo 炮台模型
 * 绑定 Geckolib 资源文件
 */
class DuoModel : GeoModel<DuoTurretBlockEntity>() {

    companion object {
        val MODEL_RESOURCE = ResourceLocation.fromNamespaceAndPath(Mindustry.MOD_ID, "geo/duo.geo.json")
        val TEXTURE_RESOURCE = ResourceLocation.fromNamespaceAndPath(Mindustry.MOD_ID, "textures/block/duo.png")
        val ANIMATION_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(Mindustry.MOD_ID, "animations/duo.animation.json")
    }

    override fun getModelResource(animatable: DuoTurretBlockEntity): ResourceLocation {
        return MODEL_RESOURCE
    }

    override fun getTextureResource(animatable: DuoTurretBlockEntity): ResourceLocation {
        return TEXTURE_RESOURCE
    }

    override fun getAnimationResource(animatable: DuoTurretBlockEntity): ResourceLocation {
        return ANIMATION_RESOURCE
    }
}
