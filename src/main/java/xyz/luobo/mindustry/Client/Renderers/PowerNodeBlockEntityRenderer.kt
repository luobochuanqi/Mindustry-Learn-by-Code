package xyz.luobo.mindustry.Client.Renderers

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import xyz.luobo.mindustry.Common.BlockEntities.PowerNodeBlockEntity
import java.util.*
import kotlin.math.acos


class PowerNodeBlockEntityRenderer(
    ctx: BlockEntityRendererProvider.Context
) : BlockEntityRenderer<PowerNodeBlockEntity> {

    /**
     * 自定义的激光线条 RenderType。
     * - 格式: POSITION_COLOR_NORMAL (位置, 颜色, 法线)
     * - 模式: LINES (线条模式)
     * - 缓冲区大小: 1536 (与 RenderType.lines 相同)
     * - affectsCrumbling: false (与 RenderType.lines 相同)
     * - sortOnUpload: false (与 RenderType.lines 相同)
     * - 输出: MAIN_TARGET (主世界渲染目标)
     * - 其他状态: 复制自 RenderType.LINES 的定义，但修改了 outputState。
     */
    @JvmField
    val POWER_NODE_LASER_LINES: RenderType = RenderType.create( // 修改这里：类型改为 RenderType
        "power_node_laser_lines",
        DefaultVertexFormat.POSITION_COLOR_NORMAL, // 与 RenderType.lines() 相同
        VertexFormat.Mode.LINES, 1536,
        RenderType.CompositeState.builder()
            .setShaderState(RenderType.RENDERTYPE_LINES_SHADER) // 与 RenderType.lines() 相同
            .setLineState(RenderStateShard.LineStateShard(OptionalDouble.empty())) // 与 RenderType.lines() 相同
            .setLayeringState(RenderType.VIEW_OFFSET_Z_LAYERING) // 与 RenderType.lines() 相同
            .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY) // 与 RenderType.lines() 相同
            // 关键修改：将输出目标从 ITEM_ENTITY_TARGET 改为 MAIN_TARGET
            .setOutputState(RenderStateShard.MAIN_TARGET) // 这是修复渲染问题的核心
            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE) // 与 RenderType.lines() 相同
            .setCullState(RenderStateShard.NO_CULL) // 与 RenderType.lines() 相同
            .createCompositeState(false) // 与 RenderType.lines() 相同
    )

    override fun render(
        blockEntity: PowerNodeBlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        // 基础调试 - 确认渲染器被调用
//        Mindustry.LOGGER.debug("PowerNodeBlockEntityRenderer.render() called for {}", blockEntity.blockPos)

        val level = Minecraft.getInstance().level ?: return
        if (!blockEntity.shouldRenderConnections) return

        val fromPos = blockEntity.blockPos
        val connections = blockEntity.getConnectedNodes()

        // 调试信息
//        if (connections.isNotEmpty()) {
//            Mindustry.LOGGER.debug("Rendering {} connections from {} to {}", connections.size, fromPos, connections)
//        }

        // 只渲染一次（避免双向重复绘制）
        // 可选：只渲染 fromPos < toPos 的连接
        for (toPos in connections) {
            if (fromPos >= toPos) continue // 避免重复

//            Mindustry.LOGGER.debug("Rendering laser from {} to {}", fromPos, toPos)
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
//        val fromCenter = Vector3f(
//            from.x + 0.5f - from.x,
//            from.y + 0.5f - from.y,
//            from.z + 0.5f - from.z
//        )
//
//        val toCenter = Vector3f(
//            to.x + 0.5f - from.x,
//            to.y + 0.5f- from.y,
//            to.z + 0.5f- from.z
//        )

        val fromCenter = Vec3.atCenterOf(from)
        val toCenter = Vec3.atCenterOf(to)

        // 计算线条的方向向量，并归一化以作为法线
        // 这对于 RenderType.lines 的光照计算很重要，避免使用零向量或常量向量
        val direction = toCenter.toVector3f().sub(fromCenter.toVector3f())
        if (direction.lengthSquared() > 0.0001f) { // 避免除以零或非常小的数
            direction.normalize()
        } else {
            // 如果两点非常接近，使用一个默认方向（例如 Y 轴正方向）
            direction.set(0f, 1f, 0f)
        }
        // 法线需要经过模型视图矩阵的变换
        val normal = direction.normalize()

        /* 1. 内层：不透明深黄发光 */
        renderLaserBeam(normal, packedLight, poseStack, bufferSource, fromCenter, toCenter,
            0.04f,                      // 半径
            1.0f, 0.8f, 0.0f, 1.0f,           // RGBA
            RenderType.solid())

        /* 2. 外层：半透明浅黄，稍大 */
        renderLaserBeam(normal, packedLight, poseStack, bufferSource, fromCenter, toCenter,
            0.10f,                      // 半径
            1.0f, 1.0f, 0.6f, 0.5f,         // RGBA
            RenderType.translucent())
    }

    /* 真正绘制一束长方体激光 */
    private fun renderLaserBeam(
        normal: Vector3f,
        packedLight: Int,
        ps: PoseStack,
        buffer: MultiBufferSource,
        start: Vec3,
        end: Vec3,
        radius: Float,
        r: Float, g: Float, b: Float, a: Float,
        layer: RenderType
    ) {
        val vc = buffer.getBuffer(layer)

        var dir = end.subtract(start)
        val len = dir.length().toFloat()
        dir = dir.normalize()

        ps.pushPose()
        /* 移到起点 */
        ps.translate(start.x, start.y, start.z)
        /* 让局部 +Y 轴与激光方向对齐 */
        val y = Vector3f(0f, 1f, 0f)
        val d = Vector3f(dir.x.toFloat(), dir.y.toFloat(), dir.z.toFloat())
        val angle = acos(y.dot(d).toDouble()).toFloat()
        val axis = Vector3f(y).cross(d)
        if (axis.lengthSquared() > 1e-6f) {
            axis.normalize()
            ps.mulPose(Axis.of(axis).rotation(angle))
        }

        val mat = ps.last().pose()

        /* 长方体：以局部坐标画 中心在 (0,0,0) 高度=len 截面正方形边长=radius*2 */
        val hw = radius // half width
        val hh = radius // half height (截面)
        val he = len / 2f // half extend (Y 方向)

        /* 六个面，每面 4 顶点，两个三角形 */
        quad(normal, packedLight, vc, mat, -hw, -he, -hh, hw, -he, -hh, hw, he, -hh, -hw, he, -hh, r, g, b, a) // -Z
        quad(normal, packedLight, vc, mat, hw, -he, hh, -hw, -he, hh, -hw, he, hh, hw, he, hh, r, g, b, a) // +Z
        quad(normal, packedLight, vc, mat, -hw, -he, hh, -hw, -he, -hh, -hw, he, -hh, -hw, he, hh, r, g, b, a) // -X
        quad(normal, packedLight, vc, mat, hw, -he, -hh, hw, -he, hh, hw, he, hh, hw, he, -hh, r, g, b, a) // +X
        quad(normal, packedLight, vc, mat, -hw, -he, hh, hw, -he, hh, hw, -he, -hh, -hw, -he, -hh, r, g, b, a) // -Y
        quad(normal, packedLight, vc, mat, -hw, he, -hh, hw, he, -hh, hw, he, hh, -hw, he, hh, r, g, b, a) // +Y

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
        vc: VertexConsumer, mat: Matrix4f,
        x: Float, y: Float, z: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        vc.addVertex(mat, x, y, z)
            .setColor(r, g, b, a)
            .setUv(0F, 0F) // 无纹理，随意
            .setLight(packedLight) // 全亮度 → 发光
            .setNormal(normal.x, normal.y, normal.z) // 法线随意
    }
}