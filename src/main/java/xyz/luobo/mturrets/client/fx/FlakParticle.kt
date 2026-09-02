package xyz.luobo.mturrets.client.fx

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.particle.SpriteSet
import net.minecraft.client.particle.TextureSheetParticle
import net.minecraft.client.renderer.LightTexture

/**
 * 火花/爆团点粒子(命中爆点火花线 + flak 爆团,上游 flakExplosion 对齐)。
 * 固定三色调色板(忽略 color):bulletYellow(#fff8e8)→ gray → lighterOrange(#f6e096),billboard 离散点近似。
 * 速度由 [xd]/[yd]/[zd] 传入(工厂从 addParticle 的 velocity 取),沿命中方向飞散。
 *
 * 基类 [TextureSheetParticle]:颜色/alpha 经 [setColor]/[setAlpha] 在 tick 里 lerp,尺寸经 [getQuadSize] 收缩。
 */
class FlakParticle(
    level: ClientLevel, x: Double, y: Double, z: Double,
    xd: Double, yd: Double, zd: Double
) : TextureSheetParticle(level, x, y, z, xd, yd, zd) {

    private val life = 20

    init {
        setLifetime(life)
        friction = 0.92f
        gravity = 0.0f
    }

    override fun getRenderType(): ParticleRenderType = ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT

    /** 爆团点:age 增长→尺寸收缩。 */
    override fun getQuadSize(deltaTick: Float): Float {
        val fin = age.toFloat() / life
        return 0.06f * (1f - fin * 0.6f)
    }

    override fun tick() {
        super.tick()
        val fin = age.toFloat() / life
        // 三色调色板(简化上游 bulletYellow→gray→lighterOrange 渐变)
        setAlpha(1f - fin)
        setColor(lerp(1f, 0.45f, fin), lerp(0.973f, 0.45f, fin), lerp(0.91f, 0.5f, fin))
    }

    override fun getLightColor(partialTick: Float): Int = LightTexture.FULL_BRIGHT

    override fun pickSprite(set: SpriteSet) {
        setSprite(set.get(0, 0))
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
