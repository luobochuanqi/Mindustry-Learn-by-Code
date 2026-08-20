package xyz.luobo.mturrets.common

import net.minecraft.core.registries.Registries
import net.minecraft.world.level.material.Fluid
import net.neoforged.neoforge.fluids.FluidType
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mturrets.MTurrets
import xyz.luobo.mturrets.common.liquids.FluidRegistry
import xyz.luobo.mturrets.common.liquids.Liquids

/**
 * 流体注册
 * 使用 FluidRegistry 自动注册所有液体，避免重复代码
 */
object ModFluids {
    // 注册器
    val MOD_FLUIDS: DeferredRegister<Fluid> = DeferredRegister.create(Registries.FLUID, MTurrets.MOD_ID)
    val MOD_FLUID_TYPES: DeferredRegister<FluidType> =
        DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, MTurrets.MOD_ID)

    /**
     * 所有液体的注册表
     * 使用 lazy 确保在首次访问时执行注册
     */
    val REGISTRIES: Map<Liquids, FluidRegistry> by lazy {
        FluidRegistry.registerAll(MOD_FLUID_TYPES, MOD_FLUIDS)
    }

    /**
     * 便捷访问方法
     */
    operator fun get(liquid: Liquids): FluidRegistry = REGISTRIES[liquid]!!

    /**
     * 获取指定液体的 Source 流体
     */
    fun getSource(liquid: Liquids) = REGISTRIES[liquid]?.source

    /**
     * 获取指定液体的 Flowing 流体
     */
    fun getFlowing(liquid: Liquids) = REGISTRIES[liquid]?.flowing

    /**
     * 获取指定液体的 FluidType
     */
    fun getType(liquid: Liquids) = REGISTRIES[liquid]?.fluidType

    /**
     * 获取指定液体的颜色
     */
    fun getColor(liquid: Liquids): Int = liquid.color

    fun register() {
        // 先触发 lazy 初始化，执行所有注册
        REGISTRIES

        // 然后注册到 MOD_BUS
        MOD_FLUIDS.register(MOD_BUS)
        MOD_FLUID_TYPES.register(MOD_BUS)
    }
}
