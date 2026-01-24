package xyz.luobo.mindustry.client.renderers

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import org.joml.Matrix4f
import org.joml.Vector3f
import xyz.luobo.mindustry.ClientConfig
import xyz.luobo.mindustry.Mindustry
import java.util.*

// Why not use BlockEntityRenderer?
// I'm so stupid.
@Deprecated(message = "This class will be re-implemented using BERenderer.")
@EventBusSubscriber(modid = Mindustry.MOD_ID, value = [Dist.CLIENT])
object MachineRenderer {
    // 使用线程安全的集合存储所有需要渲染的 BE 位置
    private val blockEntitiesToRender: MutableSet<BlockPos> =
        Collections.synchronizedSet(mutableSetOf<BlockPos>())

    // 添加需要渲染的方块实体位置
    fun addToRenderList(pos: BlockPos) {
        blockEntitiesToRender.add(pos)
    }

    // 移除不需要渲染的方块实体位置
    fun removeFromRenderList(pos: BlockPos) {
        blockEntitiesToRender.remove(pos)
    }

    private val workingOverlaySprite: TextureAtlasSprite by lazy {
        val textureManager = Minecraft.getInstance().textureManager
        val atlas = textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS) as TextureAtlas
        atlas.getSprite(ResourceLocation.fromNamespaceAndPath(Mindustry.MOD_ID, "block/kiln_working_overlay"))
    }

    @SubscribeEvent
    fun onRenderLevel(event: RenderLevelStageEvent) {
        /* 机器工作时覆盖层暂时搁置
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

        for (bePos in renderList) {
            val blockEntity = level.getBlockEntity(bePos)
            if (blockEntity !is BaseMachineBE) {
                removeFromRenderList(bePos)
                continue
            }

            // 检查方块是否还存在
            if (!level.isLoaded(bePos) || level.getBlockState(bePos).isAir) {
                removeFromRenderList(bePos)
                continue
            }

            // 检查距离 - 如果太远就跳过渲染
            if (!isPositionWithinRenderDistance(bePos, cameraPosVec)) {
                continue
            }

            // 渲染 工作 状态
            if (blockEntity.isWorking) {
                renderKilnOverlay(poseStack, bufferSource, bePos, cameraPosVec)
            }
        }
        */
    }

    /**
     * 绘制方块叠加贴图的核心方法
     * @param poseStack 渲染矩阵（控制位置、旋转、缩放）
     * @param bufferSource 渲染缓冲区
     * @param pos 方块位置
     * @param cameraPos 相机位置（用于计算偏移）
     */
    private fun renderKilnOverlay(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        pos: BlockPos,
        cameraPos: Vector3f
    ) {
        // 1. 计算渲染位置（偏移到方块中心，抵消相机视角）
        val x = pos.x - cameraPos.x + 0.5
        val y = pos.y - cameraPos.y + 0.5
        val z = pos.z - cameraPos.z + 0.5

        // 2. 保存渲染矩阵（避免影响其他渲染）
        poseStack.pushPose()

        // 3. 设置贴图位置、旋转、缩放
        poseStack.translate(x, y, z)

        // 4. 获取渲染顶点消费者（指定渲染类型，比如透明贴图用TRANSLUCENT）
        val renderType = RenderType.translucent()
        val vertexConsumer = bufferSource.getBuffer(renderType)

        // 5. 绘制四边形贴图（覆盖方块正面）
        val u0 = workingOverlaySprite.u0 // 贴图UV起始X
        val u1 = workingOverlaySprite.u1 // 贴图UV结束X
        val v0 = workingOverlaySprite.v0 // 贴图UV起始Y
        val v1 = workingOverlaySprite.v1 // 贴图UV结束Y
        val color = 1.0f // 颜色亮度（1.0为原图颜色）
        val alpha = 1.0f // 透明度（1.0不透明）
        val mat = poseStack.last().pose()

        // 绘制正面（以方块中心为原点，向Z轴负方向绘制）
        vertex(vertexConsumer, mat, -0.5f, -0.5f, -0.49f, color, color, color, alpha, u0, v0)
        vertex(vertexConsumer, mat, 0.5f, -0.5f, -0.49f, color, color, color, alpha, u1, v0)
        vertex(vertexConsumer, mat, 0.5f, 0.5f, -0.49f, color, color, color, alpha, u1, v1)
        vertex(vertexConsumer, mat, -0.5f, 0.5f, -0.49f, color, color, color, alpha, u0, v1)

        // 6. 释放渲染矩阵
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

        // 渲染距离配置（以方块为单位）
        val MAX_RENDER_DISTANCE = ClientConfig.maxRenderDistance.get() // 最大渲染距离
        val MAX_RENDER_DISTANCE_SQUARED = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE

        return distanceSquared <= MAX_RENDER_DISTANCE_SQUARED
    }

    private fun vertex(
        vc: VertexConsumer,
        mat: Matrix4f,
        x: Float, y: Float, z: Float,
        r: Float, g: Float, b: Float, a: Float,
        u: Float, v: Float
    ) {
        val normal = Vector3f(0f, 1f, 0f)
        vc.addVertex(mat, x, y, z)
            .setColor(r, g, b, a)
            .setUv(u, v)
            .setUv2(0, 0)
            .setNormal(normal.x, normal.y, normal.z)
    }
}