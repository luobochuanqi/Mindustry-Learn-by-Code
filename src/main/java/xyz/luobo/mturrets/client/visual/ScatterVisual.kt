package xyz.luobo.mturrets.client.visual

import dev.engine_room.flywheel.api.instance.Instance
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
import xyz.luobo.mturrets.client.render.TurretDebugRenderer
import xyz.luobo.mturrets.common.turrets.ScatterTurretBE
import java.util.function.Consumer
import kotlin.math.abs
import kotlin.math.sign

/**
 * Scatter 动件渲染(#34,#42,#75):Flywheel partial 模型 + TRANSFORMED 实例。
 * 旋转头(双翼)与 -mid 中段(炮管刃片)共用结构中心枢轴(锚点块内 (1,1),size=2),
 * beginFrame 逐帧向同步的目标角按 rotateSpeed 逼近(客户端积分,补包间隙平滑);
 * 中段绕真实管根铰点俯仰(rotateX,服务端 targetPitch),开火计数器单调变化 → 沿俯仰后管轴后坐脉冲。
 * 全模型资产架构(#42):静态基座 + 动件全部由本 visual 渲染,方块侧渲染为空;
 * 物品模型引用 full 模型,同一几何三条入口(世界/物品/裂纹代理)零双画。
 */
class ScatterVisual(
    ctx: VisualizationContext,
    blockEntity: ScatterTurretBE,
    partialTick: Float
) : AbstractBlockEntityVisual<ScatterTurretBE>(ctx, blockEntity, partialTick), SimpleDynamicVisual {

    /** 静态基座:恒等变换一次摆位,不逐帧更新。 */
    private val base: TransformedInstance = instanceFor(ScatterModels.BASE)
    /** 旋转头(双翼):绕结构中心(锚点块内 (1,1))竖轴转 yaw。 */
    private val head: TransformedInstance = instanceFor(ScatterModels.HEAD)
    /** -mid 中段(炮管刃片):随头转 yaw + 绕管根铰点俯仰 + 后坐沿俯仰后管轴回退。 */
    private val mid: TransformedInstance = instanceFor(ScatterModels.MID)

    // ===== 客户端动画状态(不持久化) =====

    private var initialized = false
    private var smoothYaw = 0f
    private var smoothPitch = 0f
    private var lastFire = 0L
    private var recoil = 0f

    companion object {
        /** 结构中心枢轴(锚点块内局部坐标):2×2 中心 = 锚点 + (1, 1)。 */
        const val PIVOT_X = 1f
        const val PIVOT_Z = 1f
        /** 枢轴高度:基座顶面(动件坐于基座上)。 */
        const val PIVOT_Y = 0.3125f
        /** 中段炮管铰点相对结构中心的偏移(模型 pivot (16,12,10) 换算):x 已在结构中心 0,
         *  y +0.4375 = 12/16−0.3125, z −0.375 = 10/16−1。pitch 绕此水平轴,绝不能沿用 yaw
         * 枢轴高度(0.3125)——yaw 绕竖轴与高度无关,俯仰绕水平轴则必须落在真实铰点。 */
        const val MID_PIVOT_X = 0f
        const val MID_PIVOT_Y = 0.4375f
        const val MID_PIVOT_Z = -0.375f
        /** 中段后坐行程(格):≈2px(#34 spec)。 */
        const val RECOIL_OFFSET = 0.125f
        /** 后坐衰减(每帧乘子):~10 帧归零。 */
        const val RECOIL_DECAY = 0.6f
    }
    private fun instanceFor(partial: PartialModel): TransformedInstance =
        instancerProvider().instancer(
            InstanceTypes.TRANSFORMED,
            Models.partial(partial),
            0
        ).createInstance()

    init {
        // 基座:压锚点格基准层,按 visualPos 平移摆位(结构中心即 base 几何原点,无需额外偏移);#64 修复,此前纯恒等变换落在渲染原点
        base.setIdentityTransform()
            .translate(visualPos.x.toFloat(), visualPos.y.toFloat(), visualPos.z.toFloat())
            .setChanged()
        head.setZeroTransform().setChanged()
        TurretDebugRenderer.register(blockEntity)
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
        val pitchDiff = Mth.wrapDegrees(blockEntity.targetPitch - smoothPitch)
        smoothPitch += if (abs(pitchDiff) <= step) pitchDiff else sign(pitchDiff) * step

        // 开火计数器脉冲 → 中段后坐(跨包计数跳变时取最后一发)
        val fire = blockEntity.fireCount
        if (fire != lastFire) {
            recoil = 1f
            lastFire = fire
        }
        recoil *= RECOIL_DECAY

        val yawRad = Mth.DEG_TO_RAD * smoothYaw
        val pitchRad = Mth.DEG_TO_RAD * smoothPitch
        val vx = visualPos.x.toFloat()
        val vy = visualPos.y.toFloat()
        val vz = visualPos.z.toFloat()

        // 头:绕结构中心竖轴转 yaw(模型 +Z = 枪口方向,yaw 0 = +Z,与服务端约定一致)
        head.setIdentityTransform()
            .translate(vx + PIVOT_X, vy + PIVOT_Y, vz + PIVOT_Z)
            .rotateY(yawRad)
            .translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z)
            .setChanged()

        // 中段:先随头转 yaw(铰点 x/z 中心),再绕炮管真实铰点俯仰,后坐沿俯仰后管轴回退。
        // 旋转顺序对齐 Duo poseBarrel:yaw 由 pivot 承担 → 平移至铰点 → rotateX(pitch) → 后坐。
        mid.setIdentityTransform()
            .translate(vx + PIVOT_X, vy + PIVOT_Y, vz + PIVOT_Z)
            .rotateY(yawRad)
            .translate(MID_PIVOT_X, MID_PIVOT_Y, MID_PIVOT_Z)
            .rotateX(pitchRad)
            .translate(0f, 0f, -recoil * RECOIL_OFFSET)
            .translate(-MID_PIVOT_X, -MID_PIVOT_Y, -MID_PIVOT_Z)
            .translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z)
            .setChanged()
    }

    override fun updateLight(partialTick: Float) {
        relight(base, head, mid)
    }

    override fun collectCrumblingInstances(consumer: Consumer<Instance?>) {
        consumer.accept(base)
        consumer.accept(head)
        consumer.accept(mid)
    }

    override fun _delete() {
        TurretDebugRenderer.unregister(blockEntity)
        base.delete()
        head.delete()
        mid.delete()
    }
}

/** partial 模型资产(ADR-0002 惯例:静态创建即注册,烘焙由 Flywheel 挂 ModelEvent 处理)。 */
object ScatterModels {
    private fun part(path: String): PartialModel =
        PartialModel.of(ResourceLocation.fromNamespaceAndPath(MTurrets.MOD_ID, "block/turret/$path"))

    val HEAD: PartialModel = part("scatter_head")
    val BASE: PartialModel = part("scatter_base")
    val MID: PartialModel = part("scatter_mid")

    /**
     * 预初始化:触发对象静态初始化,使各 [PartialModel.of] 在客户端早期注册。
     * 必须赶在首次资源重载的 RegisterAdditional/BakingCompleted 之前(懒加载 = 黑紫占位,见 Duo #31 教训)。
     */
    fun preload() = Unit
}