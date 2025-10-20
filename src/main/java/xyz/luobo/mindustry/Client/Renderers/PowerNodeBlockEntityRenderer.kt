package xyz.luobo.mindustry.Client.Renderers

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import org.joml.Vector3f
import xyz.luobo.mindustry.Common.BlockEntities.PowerNodeBlockEntity

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
        val fromCenter = Vector3f(
            from.x + 0.5f,
            from.y + 0.5f,
            from.z + 0.5f
        )

        val toCenter = Vector3f(
            to.x + 0.5f,
            to.y + 0.5f,
            to.z + 0.5f
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
        val normal = Vector3f(0f, 1f, 0f)
        consumer.addVertex(matrix, fromCenter.x, fromCenter.y, fromCenter.z)
            .setColor(r, g, b, a)
            .setNormal(pose, normal.x(), normal.y(), normal.z()) // 传递 PoseStack.Pose 对象
//            .setUv2(packedLight and 0xFFFF, packedLight shr 16 and 0xFFFF)
//            .setOverlay(packedOverlay)

        consumer.addVertex(matrix, toCenter.x, toCenter.y, toCenter.z)
            .setColor(r, g, b, a)
            .setNormal(pose, normal.x(), normal.y(), normal.z()) // 传递 PoseStack.Pose 对象
//            .setUv2(packedLight and 0xFFFF, packedLight shr 16 and 0xFFFF)
//            .setOverlay(packedOverlay)
        poseStack.popPose()
    }
}