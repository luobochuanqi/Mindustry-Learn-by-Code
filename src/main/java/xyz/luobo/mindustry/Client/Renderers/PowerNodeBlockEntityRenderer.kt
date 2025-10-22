package xyz.luobo.mindustry.Client.Renderers

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import org.joml.Vector3f
import xyz.luobo.mindustry.Common.BlockEntities.PowerNodeBlockEntity
import xyz.luobo.mindustry.Mindustry
import java.util.*

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
        if (connections.isNotEmpty()) {
//            Mindustry.LOGGER.debug("Rendering {} connections from {} to {}", connections.size, fromPos, connections)
        }

        // 只渲染一次（避免双向重复绘制）
        // 可选：只渲染 fromPos < toPos 的连接
        for (toPos in connections) {
            if (fromPos >= toPos) continue // 避免重复

            Mindustry.LOGGER.debug("Rendering laser from {} to {}", fromPos, toPos)
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
        val fromCenter = Vector3f(
            from.x + 0.5f - from.x,
            from.y + 0.5f - from.y,
            from.z + 0.5f - from.z
        )

        val toCenter = Vector3f(
            to.x + 0.5f - from.x,
            to.y + 0.5f- from.y,
            to.z + 0.5f- from.z
        )

        // 获取顶点消费者（使用 RenderType.lines()）
        val consumer: VertexConsumer = bufferSource.getBuffer(RenderType.lines())

        // 设置颜色（例如：蓝色激光）
        val r = 0.2f
        val g = 0.6f
        val b = 1.0f
        val a = 0.8f

        // 获取光照（可选，激光通常忽略光照）
        // val light = LevelRenderer.getLightColor(level, to)

        poseStack.pushPose()
        val pose = poseStack.last()
        val matrix = pose.pose()

        // 计算线条的方向向量，并归一化以作为法线
        // 这对于 RenderType.lines 的光照计算很重要，避免使用零向量或常量向量
        val direction = Vector3f(toCenter).sub(fromCenter)
        if (direction.lengthSquared() > 0.0001f) { // 避免除以零或非常小的数
            direction.normalize()
        } else {
            // 如果两点非常接近，使用一个默认方向（例如 Y 轴正方向）
            direction.set(0f, 1f, 0f)
        }
        // 法线需要经过模型视图矩阵的变换
        val normal = direction.normalize()

        consumer.addVertex(matrix, fromCenter.x, fromCenter.y, fromCenter.z)
            .setUv(0f, 0f)
            .setColor(r, g, b, a)
            .setNormal(pose, normal.x(), normal.y(), normal.z())
            .setLight(packedLight)
            .setOverlay(packedOverlay)

        consumer.addVertex(matrix, toCenter.x, toCenter.y, toCenter.z)
            .setUv(0f, 0f)
            .setColor(r, g, b, a)
            .setNormal(pose, normal.x(), normal.y(), normal.z())
            .setLight(packedLight)
            .setOverlay(packedOverlay)

        poseStack.popPose()
    }
}