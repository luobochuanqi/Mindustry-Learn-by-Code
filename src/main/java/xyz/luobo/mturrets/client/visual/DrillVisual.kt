package xyz.luobo.mturrets.client.visual

import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.model.Model
import dev.engine_room.flywheel.api.visual.DynamicVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.material.Materials
import dev.engine_room.flywheel.lib.model.Models
import dev.engine_room.flywheel.lib.model.baked.BakedModelBuilder
import dev.engine_room.flywheel.lib.model.baked.PartialModel
import dev.engine_room.flywheel.lib.util.RendererReloadCache
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual
import java.util.function.Consumer
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import xyz.luobo.mturrets.MTurrets
import xyz.luobo.mturrets.common.machines.drill.DrillBE

/**
 * 钻头动件渲染(ADR-0011):Flywheel partial 模型 + TRANSFORMED 实例。
 * 静态 base + 静止 top 恒等摆位;rotator(扇叶)绕固定竖直轴自旋。
 *
 * 自旋复刻 Mindustry Drill.draw():timeDrilled += warmup*delta,绘制角 = timeDrilled*rotateSpeed,
 * warmup 0→1 渐入(速率 0.015/tick),rotateSpeed=2 rad/s 恒定——档位只改产出,不改视觉转速。
 * 角度客户端自持、不持久化(纯装饰视觉,重载世界从 0 起转)。
 */
class DrillVisual(
    ctx: VisualizationContext,
    blockEntity: DrillBE,
    partialTick: Float
) : AbstractBlockEntityVisual<DrillBE>(ctx, blockEntity, partialTick), SimpleDynamicVisual {

    /** 静态基座:实心方(无透明区),默认 solid 渲染即可。不作逐帧更新。 */
    private val base: TransformedInstance = instanceFor(DrillModels.BASE)
    /** 自旋扇叶:贴图透明区(alpha=0, RGB 黑)须 alpha 裁剪,走 cutout 材质。 */
    private val rotator: TransformedInstance = instanceFor(DrillModels.ROTATOR, cutout = true)
    /** 静止盖:同扇叶有透明区,走 cutout。压在扇叶转轴上,不参与旋转。 */
    private val top: TransformedInstance = instanceFor(DrillModels.TOP, cutout = true)

    /** 累积"挖掘时间"(Mindustry timeDrilled):warmup 系数加权,决定扇叶角一轴。 */
    private var timeDrilled = 0f
    /** 转速渐入系数 0→1:开钻增、停钻减,速率 [WARMUP_SPEED]/tick。 */
    private var warmup = 0f

    companion object {
        /** 扇叶旋转速率(rad/tick),对齐 Mindustry Drill.rotateSpeed=2(rad/s ÷ 20 tick/s)。 */
        const val ROTATE_SPEED = 2f / 20f
        /** warmup 渐入渐出速率(1/tick),对齐 Mindustry Drill.warmupSpeed=0.015。 */
        const val WARMUP_SPEED = 0.015f
        /** rotator 自旋竖直轴高度(块内局部 y):贴 base 顶面,扇叶薄片中心。 */
        const val PIVOT_Y = 1.0f
    }

    private fun instanceFor(partial: PartialModel, cutout: Boolean = false): TransformedInstance =
        instancerProvider().instancer(
            InstanceTypes.TRANSFORMED,
            DrillModels.model(partial, cutout),
            0
        ).createInstance()

    init {
        // 基座/盖:压锚点格基准层,按 visualPos 平移摆位(与炮台 #64 同款修复,几何原点在锚点角)
        base.setIdentityTransform()
            .translate(visualPos.x.toFloat(), visualPos.y.toFloat(), visualPos.z.toFloat())
            .setChanged()
        rotator.setZeroTransform().setChanged()
        top.setIdentityTransform()
            .translate(visualPos.x.toFloat(), visualPos.y.toFloat(), visualPos.z.toFloat())
            .setChanged()
    }

    override fun beginFrame(context: DynamicVisual.Context) {
        val pt = context.partialTick()
        // warmup 渐入渐出:开钻(1)增、停钻(0)减,速率 WARMUP_SPEED/tick(乘帧分率)
        val target = if (blockEntity.isRunning) 1f else 0f
        warmup += if (warmup < target) WARMUP_SPEED * pt else -WARMUP_SPEED * pt
        warmup = warmup.coerceIn(0f, 1f)

        // 扇叶角累加(Mindustry timeDrilled *= warmup):满速每次 ROTATE_SPEED(rad/tick) × 帧分率 pt
        if (blockEntity.isRunning) {
            timeDrilled += warmup * ROTATE_SPEED * pt
        }

        val vx = visualPos.x.toFloat()
        val vy = visualPos.y.toFloat()
        val vz = visualPos.z.toFloat()

        // 扇叶:绕 2×2 结构中心竖轴自旋。模型 rotator 覆盖 0..32 模型单位(=2 格)、几何中心在模型
        // (16,16) = 距锚点角 +1 格(锚点在 +X/+Z 角,ADR-0003),故世界枢轴 = visualPos + 1 格。
        rotator.setIdentityTransform()
            .translate(vx + 1.0f, vy + PIVOT_Y, vz + 1.0f)
            .rotateY(timeDrilled)
            .translate(-1.0f, -PIVOT_Y, -1.0f)
            .setChanged()
    }

    override fun updateLight(partialTick: Float) {
        relight(base, rotator, top)
    }

    override fun collectCrumblingInstances(consumer: Consumer<Instance?>) {
        consumer.accept(base)
        consumer.accept(rotator)
        consumer.accept(top)
    }

    override fun _delete() {
        base.delete()
        rotator.delete()
        top.delete()
    }
}

/** partial 模型资产(ADR-0002/0011 惯例:静态创建即注册,烘焙由 Flywheel 挂 ModelEvent 处理)。 */
object DrillModels {
    private fun part(path: String): PartialModel =
        PartialModel.of(ResourceLocation.fromNamespaceAndPath(MTurrets.MOD_ID, "block/drill/$path"))

    val BASE: PartialModel = part("mechanical_drill_base")
    val ROTATOR: PartialModel = part("mechanical_drill_rotator")
    val TOP: PartialModel = part("mechanical_drill_top")

    /**
     * 模型缓存(资源重载自动失效)。cutout=true 时把 partial 模型映射到 CUTOUT_MIPPED_BLOCK
     * 材质,令扇叶/盖的透明区(alpha=0 黑 RGB)被 alpha 测试裁剪而非画成黑块。
     * partial 模型的 JSON 缺 render_type 时 baked renderType 固化 solid,烘焙期无法改 alpha,
     * 故在材质层替换——这是唯一能在不手改导出件的前提下修复透明黑块的路径。
     */
    private val cache = RendererReloadCache<Pair<PartialModel, Boolean>, Model> { (partial, cutout) ->
        if (cutout) {
            BakedModelBuilder(partial.get())
                // 3 个 RenderType 参数全忽略,无条件选 cutout 材质
                .materialFunc { _: RenderType, _: Boolean, _: Boolean -> Materials.CUTOUT_MIPPED_BLOCK }
                .build()
        } else {
            Models.partial(partial)
        }
    }

    fun model(partial: PartialModel, cutout: Boolean = false): Model = cache.get(partial to cutout)

    /**
     * 预初始化:触发对象静态初始化,使各 [PartialModel.of] 在客户端早期注册。
     * 必须赶在首次资源重载的 RegisterAdditional/BakingCompleted 之前(懒加载 = 黑紫占位,见 Duo #31 教训)。
     */
    fun preload() = Unit
}