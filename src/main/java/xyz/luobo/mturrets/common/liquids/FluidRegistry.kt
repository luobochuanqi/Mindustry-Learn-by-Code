package xyz.luobo.mturrets.common.liquids

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.material.Fluid
import net.neoforged.neoforge.fluids.BaseFlowingFluid
import net.neoforged.neoforge.fluids.FluidType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import xyz.luobo.mturrets.MTurrets
import java.util.function.Supplier

/**
 * 流体注册封装类
 * 用于简化流体注册流程，避免重复代码
 */
class FluidRegistry(
    private val fluidTypeRegister: DeferredRegister<FluidType>,
    private val fluidRegister: DeferredRegister<Fluid>,
    val liquid: Liquids
) {
    // 液体ID
    private val id: String = liquid.id

    // 液体颜色
    val color: Int = liquid.color

    // FluidType 注册
    lateinit var fluidType: DeferredHolder<FluidType, FluidType>
        private set

    // Source 流体注册
    lateinit var source: DeferredHolder<Fluid, BaseFlowingFluid.Source>
        private set

    // Flowing 流体注册
    lateinit var flowing: DeferredHolder<Fluid, BaseFlowingFluid.Flowing>
        private set

    // 属性对象（内部使用）
    private lateinit var properties: BaseFlowingFluid.Properties

    /**
     * 执行注册
     */
    fun register() {
        // 1. 注册 FluidType
        fluidType = fluidTypeRegister.register("${id}_type", Supplier {
            FluidType(
                FluidType.Properties.create()
                    .descriptionId("fluid.${MTurrets.MOD_ID}.$id")
            )
        })

        // 2. 注册 Source（延迟初始化，等 properties 准备好）
        source = fluidRegister.register(id, Supplier {
            BaseFlowingFluid.Source(properties)
        })

        // 3. 注册 Flowing
        flowing = fluidRegister.register("${id}_flowing", Supplier {
            BaseFlowingFluid.Flowing(properties)
        })

        // 4. 创建 Properties（此时 source 和 flowing 已注册但还未初始化）
        properties = BaseFlowingFluid.Properties(fluidType, source, flowing)
    }

    /**
     * 获取纹理路径（静态）
     */
    val stillTexture: ResourceLocation
        get() = ResourceLocation.withDefaultNamespace("block/water_still")

    val flowingTexture: ResourceLocation
        get() = ResourceLocation.withDefaultNamespace("block/water_flow")

    companion object {
        /**
         * 批量注册所有液体
         */
        fun registerAll(
            fluidTypeRegister: DeferredRegister<FluidType>,
            fluidRegister: DeferredRegister<Fluid>
        ): Map<Liquids, FluidRegistry> {
            return Liquids.ALL.associateWith { liquid ->
                FluidRegistry(fluidTypeRegister, fluidRegister, liquid).apply {
                    register()
                }
            }
        }
    }
}
