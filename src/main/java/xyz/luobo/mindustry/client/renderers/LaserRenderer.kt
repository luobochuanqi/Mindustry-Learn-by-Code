package xyz.luobo.mindustry.client.renderers

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import org.joml.Matrix4f
import org.joml.Vector3f
import xyz.luobo.mindustry.Mindustry
import xyz.luobo.mindustry.common.blockEntities.PowerNodeBlockEntity
import java.util.*
import kotlin.math.acos

@EventBusSubscriber(modid = Mindustry.MOD_ID, value = [Dist.CLIENT])
object LaserRenderer {
    // 渲染距离配置（以方块为单位）
    private const val MAX_RENDER_DISTANCE = 64.0 // 最大渲染距离
    private const val MAX_RENDER_DISTANCE_SQUARED = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE

    // 使用线程安全的集合存储所有需要渲染的 PowerNode 位置
    private val blockEntitiesToRender: MutableSet<BlockPos> = Collections.synchronizedSet(mutableSetOf<BlockPos>())

    // 添加需要渲染的方块实体位置
    fun addToRenderList(pos: BlockPos) {
        blockEntitiesToRender.add(pos)
    }

    // 移除不需要渲染的方块实体位置
    fun removeFromRenderList(pos: BlockPos) {
        blockEntitiesToRender.remove(pos)
    }

    @SubscribeEvent
    fun onRenderLevel(event: RenderLevelStageEvent) {
        // 在透明方块渲染后 避免被透明块遮挡
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val poseStack = event.poseStack
        val bufferSource = Minecraft.getInstance().renderBuffers().bufferSource()

        val cameraPos = Minecraft.getInstance().gameRenderer.mainCamera.position
        // 提前计算相机位置的 Vector3f 用于距离计算
        val cameraPosVec = Vector3f(cameraPos.x.toFloat(), cameraPos.y.toFloat(), cameraPos.z.toFloat())

        // 创建副本避免 ConcurrentModification
        val renderList = blockEntitiesToRender.toList()

        for (fromPos in renderList) {
            val blockEntity = level.getBlockEntity(fromPos)
            if (blockEntity !is PowerNodeBlockEntity) {
                removeFromRenderList(fromPos)
                continue
            }

            // 检查方块是否还存在
            if (!level.isLoaded(fromPos) || level.getBlockState(fromPos).isAir) {
                removeFromRenderList(fromPos)
                continue
            }

            // 检查距离 - 如果太远就跳过渲染
            if (!isPositionWithinRenderDistance(fromPos, cameraPosVec)) {
                continue
            }
            // 渲染待连接状态
            if (blockEntity.isAwaitingConnection) {
                // 渲染黄色圆圈，半径为 MAX_CONNECTION_DISTANCE
                renderCircle(fromPos, PowerNodeBlockEntity.MAX_CONNECTION_DISTANCE,
                    poseStack, bufferSource)
            }
            // 渲染连接激光
            if (blockEntity.shouldRenderConnections) {
                for (to in blockEntity.getConnectedNodes()) {
                    // 额外检查 to 是否有效
                    if (!level.isLoaded(to) || level.getBlockState(to).isAir) {
                        blockEntity.removeConnection(to) // 客户端清理（虽不持久，但避免渲染）
                        continue
                    }

                    if (fromPos < to) {
                        renderLaser(
                            fromPos, to,
                            poseStack, bufferSource
                        )
                    }
                }
            }
        }
    }

    // 渲染待连接状态的圆圈
    private fun renderCircle(
        pos: BlockPos,
        maxConnectionDistance: Double,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource.BufferSource
    ) {
        val cameraPos = Minecraft.getInstance().gameRenderer.mainCamera.position
        var vc = bufferSource.getBuffer(RenderType.lightning())

        poseStack.pushPose()
        // 平移到方块中心并相对于相机坐标
        poseStack.translate(
            pos.x - cameraPos.x.toDouble(),
            pos.y - cameraPos.y.toDouble(),
            pos.z - cameraPos.z.toDouble()
        )
        // 移动到方块中心并稍微抬高以避免 Z-fighting
        poseStack.translate(0.5, 0.51, 0.5)

        val mat = poseStack.last().pose()
        val normal = Vector3f(0f, 1f, 0f)

        val segments = 30
        val innerR = maxConnectionDistance.toFloat()
        val thickness = 0.2f
        val outerR = innerR + thickness

        val y = 0f

        // 边框厚度（围绕内外圈各自一小条黑边）
        val borderThickness = 0.1f
        val yBorder = y + 0.001f // 轻微抬高以避免 Z-fighting

        for (i in 0 until segments) {
            val a1 = (2.0 * kotlin.math.PI * i) / segments.toDouble()
            val a2 = (2.0 * kotlin.math.PI * (i + 1)) / segments.toDouble()

            val x1 = (kotlin.math.cos(a1) * innerR).toFloat()
            val z1 = (kotlin.math.sin(a1) * innerR).toFloat()
            val x2 = (kotlin.math.cos(a2) * innerR).toFloat()
            val z2 = (kotlin.math.sin(a2) * innerR).toFloat()

            val x3 = (kotlin.math.cos(a2) * outerR).toFloat()
            val z3 = (kotlin.math.sin(a2) * outerR).toFloat()
            val x4 = (kotlin.math.cos(a1) * outerR).toFloat()
            val z4 = (kotlin.math.sin(a1) * outerR).toFloat()

            // 黄色半透明环
            quad(
                normal, vc, mat,
                x1, y, z1,
                x2, y, z2,
                x3, y, z3,
                x4, y, z4,
                1.0f, 0.85f, 0.0f, 0.6f
            )

//            vc = bufferSource.getBuffer(RenderType.solid())

            // 内侧黑色边框（从 innerR - borderThickness 到 innerR）
            val ix1 = (kotlin.math.cos(a1) * (innerR - borderThickness)).toFloat()
            val iz1 = (kotlin.math.sin(a1) * (innerR - borderThickness)).toFloat()
            val ix2 = (kotlin.math.cos(a2) * (innerR - borderThickness)).toFloat()
            val iz2 = (kotlin.math.sin(a2) * (innerR - borderThickness)).toFloat()

            quad(
                normal, vc, mat,
                ix1, yBorder, iz1,
                ix2, yBorder, iz2,
                x2, yBorder, z2,
                x1, yBorder, z1,
                0.1f, 0.1f, 0.1f, 1.0f
            )

            // 外侧黑色边框（从 outerR 到 outerR + borderThickness）
            val ox1 = (kotlin.math.cos(a1) * (outerR + borderThickness)).toFloat()
            val oz1 = (kotlin.math.sin(a1) * (outerR + borderThickness)).toFloat()
            val ox2 = (kotlin.math.cos(a2) * (outerR + borderThickness)).toFloat()
            val oz2 = (kotlin.math.sin(a2) * (outerR + borderThickness)).toFloat()

            quad(
                normal, vc, mat,
                x4, yBorder, z4,
                x3, yBorder, z3,
                ox2, yBorder, oz2,
                ox1, yBorder, oz1,
                0.1f, 0.1f, 0.1f, 1.0f
            )
        }
        poseStack.popPose()
    }

    /**
     * 检查位置是否在渲染距离内
     */
    private fun isPositionWithinRenderDistance(pos: BlockPos, cameraPos: Vector3f): Boolean {
        val dx = pos.x + 0.5 - cameraPos.x
        val dy = pos.y + 0.5 - cameraPos.y
        val dz = pos.z + 0.5 - cameraPos.z
        val distanceSquared = dx * dx + dy * dy + dz * dz

        return distanceSquared <= MAX_RENDER_DISTANCE_SQUARED
    }

    private fun renderLaser(
        from: BlockPos,
        to: BlockPos,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource
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
            normal, poseStack, bufferSource, from, fromCenter, toCenter, length,
            0.04f,
            1.0f, 1.0f, 1.0f, 1.0f,
            RenderType.lightning()
        )

        /* 2. 外层：半透明浅黄，稍大 */
        renderLaserBeam(
            normal, poseStack, bufferSource, from, fromCenter, toCenter, length,
            0.10f,
            1.0f, 0.8f, 0.0f, 0.4f,
            RenderType.lightning()
        )
    }

    private fun renderLaserBeam(
        normal: Vector3f,
        ps: PoseStack,
        buffer: MultiBufferSource,
        from: BlockPos,
        fromCenter: Vector3f,
        toCenter: Vector3f,
        length: Float,
        radius: Float,
        r: Float, g: Float, b: Float, a: Float,
        layer: RenderType
    ) {
        val cameraPos = Minecraft.getInstance().gameRenderer.mainCamera.position

        val vc = buffer.getBuffer(layer)

        val direction = Vector3f(toCenter).sub(fromCenter)
        direction.normalize()

        ps.pushPose()
        // 先平移到from方块的世界坐标（x,y,z）
        ps.translate(
            from.x - cameraPos.x,
            from.y - cameraPos.y,
            from.z - cameraPos.z
        )
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

        // -Z面
        quad(
            normal, vc, mat,
            -hw, 0f, -hh,        // 左下
            -hw, length, -hh,    // 左上
            hw, length, -hh,     // 右上
            hw, 0f, -hh,         // 右下
            r, g, b, a
        )

        // +Z面
        quad(
            normal, vc, mat,
            hw, 0f, hh,          // 右下
            hw, length, hh,      // 右上
            -hw, length, hh,     // 左上
            -hw, 0f, hh,         // 左下
            r, g, b, a
        )

        // -X面
        quad(
            normal, vc, mat,
            -hw, 0f, hh,         // 右下
            -hw, length, hh,     // 右上
            -hw, length, -hh,    // 左上
            -hw, 0f, -hh,        // 左下
            r, g, b, a
        )

        // +X面
        quad(
            normal, vc, mat,
            hw, 0f, -hh,         // 左下
            hw, length, -hh,     // 左上
            hw, length, hh,      // 右上
            hw, 0f, hh,          // 右下
            r, g, b, a
        )

        // -Y面
        quad(
            normal, vc, mat,
            -hw, 0f, -hh,        // 左下
            hw, 0f, -hh,         // 右下
            hw, 0f, hh,          // 右上
            -hw, 0f, hh,         // 左上
            r, g, b, a
        )

        // +Y面
        quad(
            normal, vc, mat,
            -hw, length, hh,     // 左上
            hw, length, hh,      // 右上
            hw, length, -hh,     // 右下
            -hw, length, -hh,    // 左下
            r, g, b, a
        )

        ps.popPose()
    }

    private fun quad(
        normal: Vector3f,
        vc: VertexConsumer,
        mat: Matrix4f,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        x4: Float, y4: Float, z4: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        vertex(normal, vc, mat, x1, y1, z1, r, g, b, a)
        vertex(normal, vc, mat, x2, y2, z2, r, g, b, a)
        vertex(normal, vc, mat, x3, y3, z3, r, g, b, a)
        vertex(normal, vc, mat, x4, y4, z4, r, g, b, a)
    }

    private fun vertex(
        normal: Vector3f,
        vc: VertexConsumer,
        mat: Matrix4f,
        x: Float, y: Float, z: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        vc.addVertex(mat, x, y, z)
            .setColor(r, g, b, a)
            .setUv(0f, 0f)
            .setNormal(normal.x, normal.y, normal.z)
    }
}