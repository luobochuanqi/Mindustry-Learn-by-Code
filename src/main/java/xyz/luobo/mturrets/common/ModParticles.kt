package xyz.luobo.mturrets.common

import net.minecraft.core.particles.ParticleType
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.core.registries.Registries
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mturrets.MTurrets

/**
 * 子弹 FX 粒子注册(#62):3 个 [SimpleParticleType](data-driven,无 ParticleOptions,颜色/形状走客户端
 * 粒子类 + payload)。SimpleParticleType 构造的 boolean 是 shouldCull(粒子不做区块剔除,恒 false)。
 * 客户端由 [ModFx] 经 RegisterParticleProvidersEvent.registerSpriteSet 绑定 Sprite 粒子类。
 */
object ModParticles {
    private val PARTICLES: DeferredRegister<ParticleType<*>> =
        DeferredRegister.create(Registries.PARTICLE_TYPE, MTurrets.MOD_ID)

    private fun type(name: String): DeferredHolder<ParticleType<*>, SimpleParticleType> =
        PARTICLES.register(name, java.util.function.Supplier { SimpleParticleType(false) })

    /** 渐隐环(铜弹命中/到寿):消费 hitColor 的大/小环。 */
    val RING: DeferredHolder<ParticleType<*>, SimpleParticleType> = type("ring")
    /** 火花点/爆团(命中爆点 + flak 爆团):固定调色板,沿方向飞散。 */
    val FLAK: DeferredHolder<ParticleType<*>, SimpleParticleType> = type("flak")
    /** 小型消散(上游 hitBulletSmall 对齐):到寿小环/点。 */
    val SMALL: DeferredHolder<ParticleType<*>, SimpleParticleType> = type("small")

    fun register() {
        PARTICLES.register(MOD_BUS)
    }
}
