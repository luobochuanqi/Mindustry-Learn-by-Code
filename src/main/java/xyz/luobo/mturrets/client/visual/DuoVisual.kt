package xyz.luobo.mturrets.client.visual

import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.model.Model
import dev.engine_room.flywheel.api.visual.DynamicVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.model.Models
import dev.engine_room.flywheel.lib.model.baked.PartialModel
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import xyz.luobo.mturrets.MTurrets
import xyz.luobo.mturrets.common.turrets.DuoTurretBE
import java.util.function.Consumer
import kotlin.math.abs
import kotlin.math.sign

/**
 * Duo 动件渲染(ADR-0002/0005/0009,#31):Flywheel partial 模型 + TRANSFORMED 实例,
 * beginFrame 逐帧向同步的目标角按 rotateSpeed 逼近(客户端积分,补包间隙平滑);
 * 开火计数器(单调)变化驱动对应炮管后坐脉冲;底座静态。
 * 无回退轨道:GPU visual 缺席时方块退回静态 blockstate 模型(瓶颈 Flywheel 已 jarJar 内嵌)。
 */
class DuoVisual(
    ctx: VisualizationContext,
    blockEntity: DuoTurretBE,
    partialTick: Float
) : AbstractBlockEntityVisual<DuoTurretBE>(ctx, blockEntity, partialTick), SimpleDynamicVisual {

    // ===== 部件位姿(块内局部空间,与 bbmodel 导出的部件几何对应) =====

    /** 底座:静态,无旋转。 */
    private val base: TransformedInstance = instanceFor(DuoModels.BASE)
    /** 炮身:绕锚点中心竖轴旋转(偏航)。 */
    private val head: TransformedInstance = instanceFor(DuoModels.HEAD)
    /** 左炮管:挂在炮身上,绕自身铰点俯仰 + 后坐。 */
    private val barrelL: TransformedInstance = instanceFor(DuoModels.BARREL_L)
    /** 右炮管。 */
    private val barrelR: TransformedInstance = instanceFor(DuoModels.BARREL_R)

    // ===== 客户端动画状态(不持久化) =====

    /** 首帧用服务端当前角初始化,避免加入时从 0° 摆过来。 */
    private var initialized = false
    private var smoothYaw = 0f
    private var smoothPitch = 0f
    private var lastFire = 0L
    private var recoilL = 0f
    private var recoilR = 0f

    companion object {
        /** 后坐位移上限(格):两管共用,按开火计数器奇偶交替分配。 */
        const val RECOIL_OFFSET = 0.3f
        /** 后坐衰减(每帧乘子):~20 帧内归零。 */
        const val RECOIL_DECAY = 0.7f
        /** 炮身 yaw 铰点高度(块内局部 y)。 */
        const val HEAD_PIVOT_Y = 0.3125f
        /** 炮管铰点高度(两管共用)。 */
        const val BARREL_PIVOT_Y = 0.375f
        /** 左炮管铰点相对块中心偏移。 */
        val BARREL_L_PIVOT = floatArrayOf(-0.22f, 0f, -0.08f)
        /** 右炮管铰点相对块中心偏移。 */
        val BARREL_R_PIVOT = floatArrayOf(0.22f, 0f, -0.08f)
    }

    private fun instanceFor(partial: PartialModel): TransformedInstance =
        instancerProvider().instancer(
            InstanceTypes.TRANSFORMED,
            Models.partial(partial),
            0
        ).createInstance()

    init {
        base.setIdentityTransform()
            .translate(visualPos.x.toFloat(), visualPos.y.toFloat(), visualPos.z.toFloat())
            .setChanged()
        head.setZeroTransform().setChanged()
        barrelL.setZeroTransform().setChanged()
        barrelR.setZeroTransform().setChanged()
    }

    override fun beginFrame(context: DynamicVisual.Context) {
        val pt = context.partialTick()
        if (!initialized) {
            smoothYaw = blockEntity.yaw
            smoothPitch = blockEntity.pitch
            initialized = true
        }

        // 客户端沿目标角积分(rotateSpeed 度/tick × 帧分率),补包间隙运动连续
        val step = blockEntity.spec.rotateSpeed * pt
        val yawDiff = Mth.wrapDegrees(blockEntity.targetYaw - smoothYaw)
        smoothYaw += if (abs(yawDiff) <= step) yawDiff else sign(yawDiff) * step
        val pitchDiff = blockEntity.targetPitch - smoothPitch
        smoothPitch += if (abs(pitchDiff) <= step) pitchDiff else sign(pitchDiff) * step

        // 开火计数器脉冲 → 后坐(奇偶交替管号;跨包计数跳变时取最后一发)
        val fire = blockEntity.fireCount
        if (fire != lastFire) {
            if (((fire - 1) % 2 + 2) % 2 == 0L) recoilL = 1f else recoilR = 1f
            lastFire = fire
        }
        recoilL *= RECOIL_DECAY
        recoilR *= RECOIL_DECAY

        val yawRad = Mth.DEG_TO_RAD * smoothYaw
        val pitchRad = Mth.DEG_TO_RAD * smoothPitch
        val vx = visualPos.x.toFloat()
        val vy = visualPos.y.toFloat()
        val vz = visualPos.z.toFloat()

        // 炮身:绕 (0.5, HEAD_PIVOT_Y, 0.5) 竖轴转 yaw
        head.setIdentityTransform()
            .translate(vx + 0.5f, vy + HEAD_PIVOT_Y, vz + 0.5f)
            .rotateY(yawRad)
            .translate(-0.5f, -HEAD_PIVOT_Y, -0.5f)
            .setChanged()

        poseBarrel(barrelL, vx, vy, vz, yawRad, pitchRad, recoilL, BARREL_L_PIVOT)
        poseBarrel(barrelR, vx, vy, vz, yawRad, pitchRad, recoilR, BARREL_R_PIVOT)
    }

    /** 炮管:绕铰点俯仰 + 沿管轴后坐;yaw 已由炮身承担(铰点随 yaw 旋转)。 */
    private fun poseBarrel(
        inst: TransformedInstance,
        vx: Float,
        vy: Float,
        vz: Float,
        yawRad: Float,
        pitchRad: Float,
        recoil: Float,
        pivot: FloatArray
    ) {
        inst.setIdentityTransform()
            .translate(vx + 0.5f, vy + BARREL_PIVOT_Y, vz + 0.5f)
            .rotateY(yawRad)
            .translate(pivot[0], pivot[1], pivot[2])
            .rotateX(pitchRad)
            .translate(0f, 0f, -recoil * RECOIL_OFFSET)
            .translate(-pivot[0], -pivot[1], -pivot[2])
            .translate(-0.5f, -BARREL_PIVOT_Y, -0.5f)
            .setChanged()
    }

    override fun updateLight(partialTick: Float) {
        relight(base, head, barrelL, barrelR)
    }

    override fun collectCrumblingInstances(consumer: Consumer<Instance?>) {
        consumer.accept(base)
        consumer.accept(head)
        consumer.accept(barrelL)
        consumer.accept(barrelR)
    }

    override fun _delete() {
        base.delete()
        head.delete()
        barrelL.delete()
        barrelR.delete()
    }
}

/** partial 模型资产(ADR-0002 惯例:静态创建即注册,烘焙由 Flywheel 挂 ModelEvent 处理)。 */
object DuoModels {
    private fun part(path: String): PartialModel =
        PartialModel.of(ResourceLocation.fromNamespaceAndPath(MTurrets.MOD_ID, "block/turret/$path"))

    val BASE: PartialModel = part("duo_base")
    val HEAD: PartialModel = part("duo_head")
    val BARREL_L: PartialModel = part("duo_barrel_left")
    val BARREL_R: PartialModel = part("duo_barrel_right")

    /**
     * 预初始化:触发对象静态初始化,使各 [PartialModel.of] 在客户端早期注册。
     * 必须赶在首次资源重载的 RegisterAdditional/BakingCompleted 之前——懒加载到
     * 世界内 visual 创建时再注册会错过烘焙,部件永远拿到未烘焙的 null 模型(黑紫贴图占位)。
     */
    fun preload() = Unit
}