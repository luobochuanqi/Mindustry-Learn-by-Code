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
                // 子弹 ≤20 tick 寿命,4 = 原版箭值(#53):8 只为无人看见落点的弹丸输送 tracker 流量。
                // updateInterval=1(#76):服务端每 tick 广播位置包,客户端 vanilla LevelRenderer
                // 的 xOld→x partialTick 插值全程生效——2 与 1 实际包速都每 tick(子弹从不 push、
                // 同步数据飞行期不 dirty),仅合包机会略减,网络负载零变化。
                .updateInterval(1)
                .build(ResourceLocation.fromNamespaceAndPath(MTurrets.MOD_ID, "turret_bullet").toString())
        })

    fun register() {
        MOD_ENTITIES.register(MOD_BUS)
    }
}