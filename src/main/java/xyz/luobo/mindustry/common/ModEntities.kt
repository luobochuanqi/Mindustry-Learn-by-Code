package xyz.luobo.mindustry.common

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mindustry.Mindustry
import xyz.luobo.mindustry.common.entity.bullet.ClientSideBullet
import java.util.function.Supplier

object ModEntities {
    val MOD_ENTITIES: DeferredRegister<EntityType<*>> =
        DeferredRegister.create(Registries.ENTITY_TYPE, Mindustry.MOD_ID)

    val DUO_BULLET_ENTITY: DeferredHolder<EntityType<*>, EntityType<ClientSideBullet>> =
        MOD_ENTITIES.register("duo_bullet", Supplier {
            EntityType.Builder.of(
                ::ClientSideBullet,
                MobCategory.MISC
            )
                .sized(0.25f, 0.25f) // 设置实体大小
                .clientTrackingRange(4) // 客户端跟踪范围
                .updateInterval(1) // 更新间隔
                .build(ResourceLocation.fromNamespaceAndPath(Mindustry.MOD_ID, "duo_bullet").toString())
        })

    fun register() {
        MOD_ENTITIES.register(MOD_BUS)
    }
}