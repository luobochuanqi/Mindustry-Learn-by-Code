package xyz.luobo.mturrets.common

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mturrets.MTurrets
import xyz.luobo.mturrets.common.entity.bullet.BulletEntity
import java.util.function.Supplier

object ModEntities {
    val MOD_ENTITIES: DeferredRegister<EntityType<*>> =
        DeferredRegister.create(Registries.ENTITY_TYPE, MTurrets.MOD_ID)

    val TURRET_BULLET: DeferredHolder<EntityType<*>, EntityType<BulletEntity>> =
        MOD_ENTITIES.register("turret_bullet", Supplier {
            EntityType.Builder.of(
                ::BulletEntity,
                MobCategory.MISC
            )
                .sized(0.25f, 0.25f)
                .clientTrackingRange(8)
                .updateInterval(2)
                .build(ResourceLocation.fromNamespaceAndPath(MTurrets.MOD_ID, "turret_bullet").toString())
        })

    fun register() {
        MOD_ENTITIES.register(MOD_BUS)
    }
}