package xyz.luobo.mturrets.client.fx

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.particle.SpriteSet
import net.minecraft.client.particle.TextureSheetParticle
import net.minecraft.client.renderer.LightTexture

/**
 * 渐隐环粒子(铜弹命中/到寿,上游 hitBulletColor 对齐):消费 [color] 的扩张渐隐环,
 * billboard 近似——MC 粒子是离散 sprite,不对齐上游矢量描边,只对齐观感(橙色渐隐环)。
 * [small] = true 时缩小尺寸、缩短寿命(上游 hitBulletSmall)。
 *
 * 基类 [TextureSheetParticle]:颜色经 [setColor](init 一次),alpha 经 [setAlpha](tick 渐隐);
 * 尺寸经 [getQuadSize](扩张),sprite 经 [pickSprite](引擎按类型名建好的 SpriteSet)。
 */
class RingParticle(
    level: ClientLevel, x: Double, y: Double, z: Double,
    /** ARGB;取 RGB 渲染,忽略顶字节 */
    color: Int,
    private val small: Boolean
) : TextureSheetParticle(level, x, y, z, 0.0, 0.0, 0.0) {

    private val r = ((color shr 16) and 0xFF) / 255f
    private val g = ((color shr 8) and 0xFF) / 255f
    private val b = (color and 0xFF) / 255f
    private val life = if (small) 10 else 14

    init {
        setLifetime(life)
        setColor(r, g, b)
    }

    override fun getRenderType(): ParticleRenderType = ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT

    /** 环扩张:age 0→life,半径 base×(0.4+0.6×fin)。 */
    override fun getQuadSize(deltaTick: Float): Float {
        val fin = (age + deltaTick) / life.toFloat()
        val base = if (small) 0.12f else 0.25f
        return base * (0.4f + 0.6f * fin) * 2f
    }

    override fun tick() {
        super.tick()
        setAlpha((1f - age.toFloat() / life).coerceAtLeast(0f))
    }

    /** FULL_BRIGHT:不依赖环境光照,暗处也醒目(与子弹渲染器一致)。 */
    override fun getLightColor(partialTick: Float): Int = LightTexture.FULL_BRIGHT

    /** 引擎按类型名(mturrets:ring/small → 同名 png)建好的 SpriteSet,取首个。 */
    override fun pickSprite(set: SpriteSet) {
        setSprite(set.get(0, 0))
    }
}
