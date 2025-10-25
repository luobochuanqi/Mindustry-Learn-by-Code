package xyz.luobo.mindustry.Client.Renderers

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import org.joml.Matrix4f
import org.joml.Vector3f
import xyz.luobo.mindustry.Common.BlockEntities.PowerNodeBlockEntity
import kotlin.math.acos


class PowerNodeBlockEntityRenderer(
    ctx: BlockEntityRendererProvider.Context
) : BlockEntityRenderer<PowerNodeBlockEntity> {

    override fun render(
        blockEntity: PowerNodeBlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val level = Minecraft.getInstance().level ?: return
        if (!blockEntity.shouldRenderConnections) return

        val fromPos = blockEntity.blockPos
        val connections = blockEntity.getConnectedNodes()

        for (toPos in connections) {
            if (fromPos >= toPos) continue
            renderLaser(level, fromPos, toPos, poseStack, bufferSource, partialTick, packedLight, packedOverlay)
        }
    }

    private fun renderLaser(
        level: Level,
        from: BlockPos,
        to: BlockPos,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        partialTicks: Float,
        packedLight: Int,
        packedOverlay: Int
    ) {
        // 计算从块中心到块中心的向量
        val fromCenter = Vector3f(0.5f, 0.5f, 0.5f)
        val toCenter = Vector3f(
            (to.x - from.x).toFloat() + 0.5f,
            (to.y - from.y).toFloat() + 0.5f,
            (to.z - from.z).toFloat() + 0.5f
        )

        // 计算方向向量和长度
        val direction = Vector3f(toCenter).sub(fromCenter)
        val length = direction.length()

        // 避免除以零
        if (length < 0.001f) return

        direction.normalize()
        val normal = Vector3f(direction)

        /* 1. 内层：不透明深黄发光 */
        renderLaserBeam(
            normal, packedLight, poseStack, bufferSource, fromCenter, toCenter, length,
            0.04f,
            1.0f, 1.0f, 1.0f, 1.0f,
            RenderType.lightning()
        )

        /* 2. 外层：半透明浅黄，稍大 */
        renderLaserBeam(
            normal, packedLight, poseStack, bufferSource, fromCenter, toCenter, length,
            0.10f,
            1.0f, 0.8f, 0.0f, 0.4f,
            RenderType.lightning()
        )
    }

    /* 真正绘制一束长方体激光 */
    private fun renderLaserBeam(
        normal: Vector3f,
        packedLight: Int,
        ps: PoseStack,
        buffer: MultiBufferSource,
        fromCenter: Vector3f,
        toCenter: Vector3f,
        length: Float,
        radius: Float,
        r: Float, g: Float, b: Float, a: Float,
        layer: RenderType
    ) {
        val vc = buffer.getBuffer(layer)

        val direction = Vector3f(toCenter).sub(fromCenter)
        direction.normalize()

        ps.pushPose()
        /* 移到起点 */
        ps.translate(fromCenter.x, fromCenter.y, fromCenter.z)

        /* 计算旋转使Y轴对齐到激光方向 */
        val yAxis = Vector3f(0f, 1f, 0f)
        val rotationAxis = Vector3f(yAxis).cross(direction)

        if (rotationAxis.lengthSquared() > 1e-12f) {
            rotationAxis.normalize()
            val dot = yAxis.dot(direction)
            val rotationAngle = acos(dot.coerceIn(-1.0f, 1.0f).toDouble()).toFloat()
            ps.mulPose(Axis.of(rotationAxis).rotation(rotationAngle))
        } else {
            // 如果方向与Y轴平行但相反，需要旋转180度
            if (yAxis.dot(direction) < 0) {
                ps.mulPose(Axis.ZP.rotationDegrees(180f))
            }
        }

        val mat = ps.last().pose()

        /* 长方体：以局部坐标画，从 (0,0,0) 到 (0, length, 0) */
        val hw = radius // half width
        val hh = radius // half height (截面)

        // -Z面（朝向左后方）
        quad(
            normal, packedLight, vc, mat,
            -hw, 0f, -hh,        // 左下
            -hw, length, -hh,    // 左上
            hw, length, -hh,     // 右上
            hw, 0f, -hh,         // 右下
            r, g, b, a           // 颜色参数
        )

        // +Z面（朝向右前方）
        quad(
            normal, packedLight, vc, mat,
            hw, 0f, hh,          // 右下
            hw, length, hh,      // 右上
            -hw, length, hh,     // 左上
            -hw, 0f, hh,         // 左下
            r, g, b, a           // 颜色参数
        )

        // -X面（朝向左侧）
        quad(
            normal, packedLight, vc, mat,
            -hw, 0f, hh,         // 右下
            -hw, length, hh,     // 右上
            -hw, length, -hh,    // 左上
            -hw, 0f, -hh,        // 左下
            r, g, b, a           // 颜色参数
        )

        // +X面（朝向右侧）
        quad(
            normal, packedLight, vc, mat,
            hw, 0f, -hh,         // 左下
            hw, length, -hh,     // 左上
            hw, length, hh,      // 右上
            hw, 0f, hh,          // 右下
            r, g, b, a           // 颜色参数
        )

        // -Y面（底部）
        quad(
            normal, packedLight, vc, mat,
            -hw, 0f, -hh,        // 左下
            hw, 0f, -hh,         // 右下
            hw, 0f, hh,          // 右上
            -hw, 0f, hh,         // 左上
            r, g, b, a           // 颜色参数
        )

        // +Y面（顶部）
        quad(
            normal, packedLight, vc, mat,
            -hw, length, hh,     // 左上
            hw, length, hh,      // 右上
            hw, length, -hh,     // 右下
            -hw, length, -hh,    // 左下
            r, g, b, a           // 颜色参数
        )

        ps.popPose()
    }

    // 绘制矩形
    private fun quad(
        normal: Vector3f,
        packedLight: Int,
        vc: VertexConsumer,
        mat: Matrix4f,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        x4: Float, y4: Float, z4: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        vertex(normal, packedLight, vc, mat, x1, y1, z1, r, g, b, a)
        vertex(normal, packedLight, vc, mat, x2, y2, z2, r, g, b, a)
        vertex(normal, packedLight, vc, mat, x3, y3, z3, r, g, b, a)
        vertex(normal, packedLight, vc, mat, x4, y4, z4, r, g, b, a)
    }

    // 添加顶点
    private fun vertex(
        normal: Vector3f,
        packedLight: Int,
        vc: VertexConsumer,
        mat: Matrix4f,
        x: Float, y: Float, z: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        vc.addVertex(mat, x, y, z)
            .setColor(r, g, b, a)
            .setUv(0f, 0f)
            .setLight(0xF000F0)
            .setNormal(normal.x, normal.y, normal.z)
    }
}