package xyz.luobo.mindustry.common

import net.minecraft.core.registries.Registries
import net.minecraft.world.level.material.Fluid
import net.neoforged.neoforge.fluids.BaseFlowingFluid
import net.neoforged.neoforge.fluids.FluidType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mindustry.Mindustry
import java.util.function.Supplier


/**
 * @author luobo
 * @date 2026/2/18
 */
object ModFluids {
    val MOD_FLUIDS: DeferredRegister<Fluid> = DeferredRegister.create(Registries.FLUID, Mindustry.MOD_ID)
    val MOD_FLUID_TYPES: DeferredRegister<FluidType> =
        DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, Mindustry.MOD_ID)

    // 流体类型注册
    val WATER_TYPE: DeferredHolder<FluidType, FluidType> = MOD_FLUID_TYPES.register("water_type", Supplier<FluidType> {
        FluidType(
            FluidType.Properties.create()
                .descriptionId("fluid.${Mindustry.MOD_ID}.water")
        )
    })

    // source
    val WATER: DeferredHolder<Fluid, BaseFlowingFluid.Source> =
        MOD_FLUIDS.register("water", Supplier<BaseFlowingFluid.Source> {
            BaseFlowingFluid.Source(WaterFluidProperties.PROPERTIES)
        })

    // flowing
    val WATER_FLOWING: DeferredHolder<Fluid, BaseFlowingFluid.Flowing> =
        MOD_FLUIDS.register("water_flowing", Supplier<BaseFlowingFluid.Flowing> {
            BaseFlowingFluid.Flowing(WaterFluidProperties.PROPERTIES)
        })

    // Properties 关联用
    object WaterFluidProperties {
        val PROPERTIES: BaseFlowingFluid.Properties = BaseFlowingFluid.Properties(
            WATER_TYPE,      // 关联你已经写好的 FluidType
            WATER,           // 关联 Source
            WATER_FLOWING    // 关联 Flowing
        )
        // 注意：这里不要调用 .block() 和 .bucket()
        // 这样它在游戏中就没有对应的方块和桶物品
    }

    fun register() {
        MOD_FLUIDS.register(MOD_BUS)
        MOD_FLUID_TYPES.register(MOD_BUS)
    }
}