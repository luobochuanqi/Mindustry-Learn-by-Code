package xyz.luobo.mturrets.client.render

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import xyz.luobo.mturrets.MTurrets
import xyz.luobo.mturrets.core.combat.TurretBE
import java.util.OptionalDouble

/**
 * 炮台 LOS 视线 debug 可视化(#77):`/mturrets debug` 开启后,每台在役 Turret 在世界中画出枪口
 * 视线——绿=射线先命中 Monster(并描其包围盒),红=先命中方块(白/黄描挡点方块),射程内两者皆无
 * 则不画。另叠一条半透明参考线表示服务端瞄准意图(targetYaw/targetPitch):旋转未到位时两线分离,
 * 一眼看出「还在转、没进开火锥角」。
 *
 * 纯客户端重建:不新增服务端→客户端同步(ADR-0005),几何调用 [TurretBE] companion 的两端共用函数。
 * 自定义 RenderType 关深度测试,线/框隔地形仍可见。
 */
@EventBusSubscriber(modid = MTurrets.MOD_ID, value = [Dist.CLIENT])
object TurretDebugRenderer {
    /** 会话内开关,不落盘;由客户端命令翻转。 */
    @Volatile
    var enabled: Boolean = false

    /**
     * 在役炮台登记表。炮台几何由 Flywheel visual 承担、无 BlockEntityRenderer,故
     * `LevelRenderer.iterateVisibleBlockEntities` 取不到它们;改由各炮台 visual 在创建/销毁时
     * 注册/注销(visual 生命周期严格绑定 BE 的客户端存在),渲染时再按 isRemoved 兜底清理。
     */
    private val turrets = HashMap<BlockPos, TurretBE>()

    /** visual 创建时调用:登记自身(始终登记,开关只在渲染时判定,故会话中途开启即生效)。 */
    fun register(turret: TurretBE) {
        turrets[turret.blockPos] = turret
    }

    /** visual 销毁时调用:注销。 */
    fun unregister(turret: TurretBE) {
        turrets.remove(turret.blockPos, turret)
    }

    /** 与 RenderType.lines() 同顶点格式,但关深度测试(隔墙可见)。 */
    private val DEBUG_LINES: RenderType = RenderType.create(
        "mturrets_debug_lines",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        VertexFormat.Mode.LINES,
        2048,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
            .setLineState(RenderStateShard.LineStateShard(OptionalDouble.empty()))
            .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
            .setCullState(RenderStateShard.NO_CULL)
            .createCompositeState(false)
    )

    @SubscribeEvent
    fun onRenderLevelStage(event: RenderLevelStageEvent) {
        if (!enabled || event.stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val camera = event.camera.position

        turrets.entries.removeAll { (_, be) -> be.isRemoved || be.level !== level }
        if (turrets.isEmpty()) return

        val buffer = mc.renderBuffers().bufferSource().getBuffer(DEBUG_LINES)
        val pose = event.poseStack
        pose.pushPose()
        pose.translate(-camera.x, -camera.y, -camera.z)
        // 登记表由 Flywheel visual 生命周期维护,天然只含在渲染(视野内)的炮台
        for (turret in turrets.values) drawTurret(level, pose, buffer, turret)
        pose.popPose()
    }

    private fun drawTurret(level: ClientLevel, pose: PoseStack, buffer: VertexConsumer, turret: TurretBE) {
        val spec = turret.spec
        val center = TurretBE.structureCenter(turret.blockPos, spec.size)
        val dir = TurretBE.directionFromAngles(turret.yaw, turret.pitch)
        val muzzle = TurretBE.muzzlePoint(center, center.add(dir), TurretBE.losMuzzleDistance(spec.size))
        val reach = spec.range.toDouble()
        val end = muzzle.add(dir.scale(reach))

        when (val ray = castRay(level, muzzle, end, spec.targetAir, spec.targetGround)) {
            is RayHit -> {
                drawLine(pose, buffer, muzzle, ray.at, 0.25f, 1f, 0.25f, 1f)
                LevelRenderer.renderLineBox(pose, buffer, ray.box, 0.25f, 1f, 0.25f, 1f)
            }

            is RayBlocked -> {
                drawLine(pose, buffer, muzzle, ray.at, 1f, 0.25f, 0.25f, 1f)
                LevelRenderer.renderLineBox(pose, buffer, AABB(ray.blocker), 1f, 1f, 0.2f, 1f)
            }
            // 射程内既无 Monster 也无方块:不画
            null -> {}
        }

        // 参考线:服务端瞄准意图(旋转中与主诊断线分离)
        val refDir = TurretBE.directionFromAngles(turret.targetYaw, turret.targetPitch)
        drawLine(pose, buffer, muzzle, muzzle.add(refDir.scale(reach)), 0.4f, 0.6f, 1f, 0.35f)
    }

    /** 射线终点状态:命中 Monster(描其盒)/ 被方块挡(描该方块)。 */
    private class RayHit(val at: Vec3, val box: AABB)
    private class RayBlocked(val at: Vec3, val blocker: BlockPos)

    /**
     * 取「最近方块阻挡」与「最近 Monster 段-盒进入」之近者;口径对位 ADR-0010 的最早进入者。
     * 非敌对实体不入候选(阵营过滤同服务端 isValidTarget)。返回 null = 射程内两者皆无。
     */
    private fun castRay(
        level: ClientLevel,
        from: Vec3,
        to: Vec3,
        targetAir: Boolean,
        targetGround: Boolean
    ): Any? {
        val block = level.clip(
            ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())
        )
        val blockDist = if (block.type == HitResult.Type.BLOCK) from.distanceToSqr(block.location) else Double.MAX_VALUE

        var hitAt: Vec3? = null
        var hitBox: AABB? = null
        var monsterDist = Double.MAX_VALUE
        for (entity in level.getEntitiesOfClass(LivingEntity::class.java, AABB(from, to).inflate(1.0)) {
                it is Monster && it.isAlive
            }) {
            if (!((targetAir && TurretBE.isAirUnit(entity)) || (targetGround && !TurretBE.isAirUnit(entity)))) continue
            val entry = entity.boundingBox.inflate(0.15).clip(from, to).orElse(null) ?: continue
            val d = from.distanceToSqr(entry)
            if (d < monsterDist) {
                monsterDist = d
                hitAt = entry
                hitBox = entity.boundingBox
            }
        }

        val at = hitAt
        val box = hitBox
        return when {
            at != null && box != null && monsterDist <= blockDist -> RayHit(at, box)
            blockDist != Double.MAX_VALUE -> RayBlocked(block.location, BlockPos.containing(block.location))
            else -> null
        }
    }

    private fun drawLine(
        pose: PoseStack, buffer: VertexConsumer, from: Vec3, to: Vec3,
        r: Float, g: Float, b: Float, a: Float
    ) {
        val n = to.subtract(from).normalize()
        val p = pose.last()
        buffer.addVertex(p, from.x.toFloat(), from.y.toFloat(), from.z.toFloat())
            .setColor(r, g, b, a).setNormal(p, n.x.toFloat(), n.y.toFloat(), n.z.toFloat())
        buffer.addVertex(p, to.x.toFloat(), to.y.toFloat(), to.z.toFloat())
            .setColor(r, g, b, a).setNormal(p, n.x.toFloat(), n.y.toFloat(), n.z.toFloat())
    }
}
