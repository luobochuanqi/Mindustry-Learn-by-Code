package xyz.luobo.mturrets.client.fx

import net.minecraft.client.multiplayer.ClientLevel
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent
import xyz.luobo.mturrets.common.BulletFxPayload
import xyz.luobo.mturrets.common.ModParticles
import xyz.luobo.mturrets.core.combat.BulletFx

/**
 * 子弹 FX 客户端消费端(#62)。
 *
 * payload 携带"选型+颜色+位置"——命中/到寿时服务端在命中点显式发,客户端在 [pos] 放对应粒子。
 * 数据驱动:弹种表不进客户端,客户端只认解析好的描述符。纯客户端表现,专用服务端不触发。
 *
 * 颜色传递:1.21.1 的 [SimpleParticleType] 不携带 ParticleOptions,`addParticle` 无法直接喂逐实例
 * 颜色;[pendingColor] 在 [dispatch] 里同步 set、由粒子工厂(同线程、`addParticle` 内同步调用)
 * 消费——与上游"color 随特效 at 传入"对齐。主线程单发,无竞态。
 */
object ModFxClient {
    /** 下一次粒子生成要用的 ARGB 颜色(dispatch 同步 set,工厂同步 read)。FLAK 固定调色板不消费。 */
    var pendingColor: Int = 0xFFFFFF
        private set

    /** payload 到达时调用(已在 main thread):按选型在 [pos] 放粒子。 */
    fun dispatch(level: ClientLevel, payload: BulletFxPayload) {
        val x = payload.pos.x
        val y = payload.pos.y
        val z = payload.pos.z
        when (payload.fx) {
            BulletFx.RING -> {
                pendingColor = payload.color
                // 渐隐环(消费 hitColor)
                level.addParticle(ModParticles.RING.get(), x, y, z, 0.0, 0.0, 0.0)
                // 5 火花线(离散点近似,朝随机水平方向飞散,对齐上游 hitBulletColor 的 5 条火花)
                repeat(5) {
                    val ang = Math.random() * Math.PI * 2.0
                    level.addParticle(
                        ModParticles.FLAK.get(), x, y, z,
                        Math.cos(ang) * 0.15, Math.sin(ang) * 0.05, Math.sin(ang) * 0.15
                    )
                }
            }
            BulletFx.FLAK -> {
                // 三色爆团(固定调色板,忽略 color)+ 5 火花点,对齐上游 flakExplosion
                repeat(5) {
                    val ang = Math.random() * Math.PI * 2.0
                    val r = 0.05 + Math.random() * 0.12
                    level.addParticle(
                        ModParticles.FLAK.get(), x, y, z,
                        Math.cos(ang) * r, Math.sin(ang) * r * 0.5, Math.sin(ang) * r
                    )
                }
            }
            BulletFx.SMALL -> {
                pendingColor = payload.color
                // 小型消散(到寿,消费 hitColor)
                level.addParticle(ModParticles.SMALL.get(), x, y, z, 0.0, 0.0, 0.0)
            }
        }
    }

    /**
     * 注册粒子工厂(客户端 mod bus 的 [RegisterParticleProvidersEvent])。
     * 用 [RegisterParticleProvidersEvent.registerSprite]:引擎按类型名自动建 SpriteSet
     * (mturrets:ring → textures/particle/ring.png),工厂只 [net.minecraft.client.particle.Particle] 化。
     */
    fun registerParticleProviders(event: RegisterParticleProvidersEvent) {
        event.registerSprite(ModParticles.RING.get()) { _, level, x, y, z, _, _, _ ->
            RingParticle(level, x, y, z, pendingColor, small = false)
        }
        event.registerSprite(ModParticles.SMALL.get()) { _, level, x, y, z, _, _, _ ->
            RingParticle(level, x, y, z, pendingColor, small = true)
        }
        event.registerSprite(ModParticles.FLAK.get()) { _, level, x, y, z, xd, yd, zd ->
            FlakParticle(level, x, y, z, xd, yd, zd)
        }
    }
}
