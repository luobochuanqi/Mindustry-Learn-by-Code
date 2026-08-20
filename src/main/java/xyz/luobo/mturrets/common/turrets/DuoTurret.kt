package xyz.luobo.mturrets.common.turrets

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import software.bernie.geckolib.animatable.GeoBlockEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.util.GeckoLibUtil
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.common.ModEntities
import xyz.luobo.mturrets.common.entity.bullet.TurretBulletEntity
import xyz.luobo.mturrets.core.turret.bullet.BulletType
import xyz.luobo.mturrets.core.turret.bullet.EffectType
import xyz.luobo.mturrets.core.turret.config.TurretConfig
import xyz.luobo.mturrets.core.turret.entity.ItemTurretBlockEntity
import xyz.luobo.mturrets.core.turret.logic.LeadCalculator

/**
 * Duo 炮台
 * MTurrets 的经典双管炮台
 * 使用铜/铅作为弹药，射速快，伤害适中
 *
 * 实现 GeoBlockEntity 以支持 Geckolib 动画
 */
class DuoTurretBlockEntity(
    pos: BlockPos,
    state: BlockState
) : ItemTurretBlockEntity(
    ModBlockEntityTypes.DUO_BLOCK_ENTITY.get(),
    pos,
    state
), GeoBlockEntity {

    // Geckolib 4 实例缓存
    private val cache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        // 纯代码驱动视角不需要注册动画控制器
        // 瞄准动画完全由代码控制(通过 Renderer 更新骨骼旋转)
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    override val config = TurretConfig(
        identifier = "duo",
        description = "Basic dual turret. Fires quick, low-damage bullets at enemies.",
        range = 20f,
        reloadTime = 15f,  // 0.75秒/发
        maxAmmo = 30,
        ammoPerShot = 1,
        inaccuracy = 2f,
        targetAir = true,
        targetGround = true,
        rotateSpeed = 60f,
        recoil = 1f,
        shootWarmupSpeed = 0.15f
    )

    /**
     * 弹药映射表
     * 铜:基础伤害
     * 铅:更高伤害,略慢
     * 铁:穿透
     */
    override val ammoTypes = mapOf(
        Items.COPPER_INGOT to BulletType(
            damage = 5f,
            speed = 3.5f,
            lifetime = 40,
            shootEffect = EffectType.SMALL_SHOOT,
            hitEffect = EffectType.SMALL_HIT,
            trailEffect = EffectType.SMALL_TRAIL
        ),
        Items.IRON_INGOT to BulletType(
            damage = 8f,
            speed = 4f,
            lifetime = 45,
            pierce = true,
            pierceCap = 2,
            pierceDamageFactor = 0.7f,
            shootEffect = EffectType.MEDIUM_SHOOT,
            hitEffect = EffectType.MEDIUM_HIT,
            trailEffect = EffectType.MEDIUM_TRAIL
        ),
        Items.GOLD_INGOT to BulletType(
            damage = 12f,
            speed = 5f,
            lifetime = 35,
            shootEffect = EffectType.MEDIUM_SHOOT,
            hitEffect = EffectType.MEDIUM_HIT,
            trailEffect = EffectType.ENERGY_TRAIL
        )
    )

    override fun fireProjectile(level: Level, pos: BlockPos, target: LivingEntity) {
        val bulletType = currentBulletType ?: return
        val shootPos = getShootPos()

        // 提前量:向预测命中位置射击,而非目标当前位置
        val direction = calculateFireDirection(target, bulletType, shootPos)

        // 创建服务端子弹实体
        val bullet = ModEntities.TURRET_BULLET.get().create(level)
        if (bullet != null) {
            bullet.moveTo(shootPos.x, shootPos.y, shootPos.z, 0f, 0f)
            bullet.init(bulletType, direction, null)
            level.addFreshEntity(bullet)
        }

        // 触发效果
        onShoot(level, pos)
    }

    /**
     * 计算射击方向(提前量 + 散布)
     */
    private fun calculateFireDirection(
        target: LivingEntity,
        bulletType: BulletType,
        shootPos: Vec3
    ): Vec3 {
        // 基于提前量方程求解命中方向
        val relativePos = target.position().subtract(shootPos)
        val timeToHit = LeadCalculator.solveLeadEquation(relativePos, target.deltaMovement, bulletType.speed.toDouble())
        val aimPoint = if (timeToHit > 0) {
            target.position().add(target.deltaMovement.scale(timeToHit))
        } else {
            target.position()
        }

        var direction = aimPoint.subtract(shootPos).normalize()

        // 应用散布
        if (config.inaccuracy > 0) {
            direction = bulletType.calculateVelocity(
                config.inaccuracy,
                direction
            )
        }

        return direction
    }

    /**
     * 瞄准点使用提前量预测位置,使炮口与子弹方向一致
     */
    override fun getAimPoint(target: LivingEntity): Vec3 {
        val bulletType = currentBulletType ?: return target.position()
        val shootPos = getShootPos()
        val timeToHit = LeadCalculator.solveLeadEquation(
            target.position().subtract(shootPos),
            target.deltaMovement,
            bulletType.speed.toDouble()
        )
        return if (timeToHit > 0) {
            target.position().add(target.deltaMovement.scale(timeToHit))
        } else {
            target.position()
        }
    }

    override fun onShoot(level: Level, pos: BlockPos) {
        super.onShoot(level, pos)

        // 播放射击音效
        // level.playSound(null, pos, ModSounds.DUO_SHOOT.get(), SoundSource.BLOCKS, 1.0f, 1.0f)
    }
}