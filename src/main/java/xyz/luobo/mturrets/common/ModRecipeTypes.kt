package xyz.luobo.mturrets.common

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mturrets.MTurrets
import xyz.luobo.mturrets.core.recipe.MachineRecipe
import java.util.function.Supplier

/**
 * 机器工艺配方通道(ADR-0006):RecipeType/Serializer 代码注册,
 * 配方实例为纯 datapack JSON,可被数据包覆盖重平衡。
 */
object ModRecipeTypes {
    val RECIPE_TYPES: DeferredRegister<RecipeType<*>> =
        DeferredRegister.create(Registries.RECIPE_TYPE, MTurrets.MOD_ID)

    val RECIPE_SERIALIZERS: DeferredRegister<RecipeSerializer<*>> =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, MTurrets.MOD_ID)

    val MACHINE: DeferredHolder<RecipeType<*>, RecipeType<MachineRecipe>> =
        RECIPE_TYPES.register("machine", Supplier {
            RecipeType.simple(ResourceLocation.fromNamespaceAndPath(MTurrets.MOD_ID, "machine"))
        })

    val MACHINE_SERIALIZER: DeferredHolder<RecipeSerializer<*>, MachineRecipe.Serializer> =
        RECIPE_SERIALIZERS.register("machine", Supplier { MachineRecipe.Serializer() })

    fun register() {
        RECIPE_TYPES.register(MOD_BUS)
        RECIPE_SERIALIZERS.register(MOD_BUS)
    }
}
