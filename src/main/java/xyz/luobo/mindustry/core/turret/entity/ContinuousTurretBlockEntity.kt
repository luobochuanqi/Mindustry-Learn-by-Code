package xyz.luobo.mindustry.core.turret.entity

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mindustry.core.turret.bullet.BulletType

/**
 * 持续射击炮台实体（激光/光束炮台）
 * 用于激光、光束等持续伤害武器
 * 模仿 Mindustry 的 ContinuousTurret / ContinuousBulletType
 *
 * @param type BlockEntityType
 * @param pos 方块位置
 * @param state 方块状态
 */
abstract class ContinuousTurretBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : PowerTurretBlockEntity(type, pos, state) {

    // ========== 持续射击配置（子类必须实现）==========

    /**
     * 每秒消耗的电力（持续消耗）
     */
    abstract val powerPerSecond: Float

    /**
     * 伤害间隔（tick）
     * 每隔这么多 tick 造成一次伤害
     */
    open val damageInterval: Int = 5

    /**
     * 需要预热才能开始射击
     */
    open val requiresWarmupToFire: Boolean = true

    /**
     * 最小预热值（0-1）
     */
    open val minWarmupToFire: Float = 0.8f

    // ========== 持续射击状态 ==========

    /**
     * 是否正在持续射击
     */
    var isFiring: Boolean = false
        private set

    /**
     * 伤害计时器
     */
    private var damageTimer: Int = 0

    /**
     * 总射击时间（tick）
     */
    var fireDuration: Int = 0
        private set

    /**
     * 当前目标累积受到的伤害（用于统计）
     */
    var totalDamageDealt: Float = 0f
        private set

    // ========== Tick 逻辑 ==========

    override fun tickServer(level: Level, pos: BlockPos, state: BlockState) {
        // 如果正在射击，更新持续射击逻辑
        if (isFiring) {
            updateContinuousFire(level, pos)
        }

        // 调用父类逻辑（装填、旋转等）
        super.tickServer(level, pos, state)
    }

    // ========== 持续射击逻辑 ==========

    /**
     * 更新持续射击
     */
    protected open fun updateContinuousFire(level: Level, pos: BlockPos) {
        // 检查是否有足够电力
        val powerNeeded = (powerPerSecond / 20f).toInt()
        if (!hasEnergy(powerNeeded)) {
            stopFiring()
            return
        }

        // 检查目标是否仍然有效
        if (currentTarget == null || !isValidTarget(currentTarget)) {
            stopFiring()
            return
        }

        // 检查是否仍然对准目标
        if (!canAttack()) {
            // 可以选择停止射击或继续跟踪
            // stopFiring()
            // return
        }

        // 消耗电力
        consumeEnergy(powerNeeded)

        // 计时器递增
        damageTimer++
        fireDuration++

        // 触发持续射击 tick 回调
        onContinuousFireTick(level, pos, currentTarget!!)

        // 造成伤害
        if (damageTimer >= damageInterval) {
            damageTimer = 0
            applyContinuousDamage(level, pos, currentTarget!!)
        }

        setChanged()
    }

    /**
     * 应用持续伤害
     */
    protected open fun applyContinuousDamage(level: Level, pos: BlockPos, target: LivingEntity) {
        val bulletType = shootType

        // 计算实际伤害（根据时间间隔转换）
        val damagePerTick = bulletType.damage * damageInterval / 20f

        // 造成伤害
        val actualDamage = target.hurt(
            level.damageSources().magic(),
            damagePerTick
        )

        if (actualDamage) {
            totalDamageDealt += damagePerTick

            // 应用状态效果
            if (bulletType.hasStatusEffects) {
                applyStatusEffects(target, bulletType)
            }

            // 触发击中回调
            onHitTarget(level, pos, target, damagePerTick)
        }

        // 应用范围伤害（如果是范围激光）
        if (bulletType.hasSplash) {
            applySplashDamage(level, pos, target, bulletType)
        }
    }

    /**
     * 应用范围伤害
     */
    protected open fun applySplashDamage(
        level: Level,
        pos: BlockPos,
        target: LivingEntity,
        bulletType: BulletType
    ) {
        val radius = bulletType.splashRadius.toDouble()
        val area = target.boundingBox.inflate(radius)

        val entities = level.getEntitiesOfClass(
            LivingEntity::class.java,
            area
        ) { it != target && it.isAlive }

        entities.forEach { entity ->
            val distance = entity.distanceTo(target).toFloat()
            if (distance <= bulletType.splashRadius) {
                // 距离衰减
                val factor = 1f - (distance / bulletType.splashRadius)
                val splashDmg = bulletType.splashDamage * factor

                entity.hurt(level.damageSources().magic(), splashDmg)
            }
        }
    }

    /**
     * 应用状态效果
     */
    protected open fun applyStatusEffects(target: LivingEntity, bulletType: BulletType) {
        // 实际实现需要将 StatusEffect 转换为 Minecraft 的 MobEffect
        // 这里简化处理
        bulletType.statusEffects.forEach { statusEffect ->
            // 应用效果逻辑
            // 例如：燃烧 -> 设置实体燃烧
            // 冰冻 -> 添加缓慢效果
        }
    }

    /**
     * 开始持续射击
     */
    protected open fun startFiring() {
        if (!isFiring) {
            isFiring = true
            damageTimer = 0
            fireDuration = 0
            setChanged()
        }
    }

    /**
     * 停止持续射击
     */
    protected open fun stopFiring() {
        if (isFiring) {
            isFiring = false
            damageTimer = 0
            fireDuration = 0
            onStopFiring()
            setChanged()
        }
    }

    // ========== 重写父类方法 ==========

    override fun canShoot(): Boolean {
        val baseCanShoot = super.canShoot()

        // 如果需要预热，检查预热值
        if (requiresWarmupToFire) {
            return baseCanShoot && warmup >= minWarmupToFire
        }

        return baseCanShoot
    }

    /**
     * 尝试射击（对于持续射击炮台，控制开始/停止）
     */
    override fun tryShoot(level: Level, pos: BlockPos) {
        if (canShoot() && currentTarget != null && canAttack()) {
            startFiring()
        } else {
            stopFiring()
        }
    }

    /**
     * 持续射击炮台不发射传统投射物
     */
    override fun fireProjectile(level: Level, pos: BlockPos, target: LivingEntity) {
        // 持续射击炮台不发射传统投射物
        // 伤害在 updateContinuousFire 中持续处理
    }

    // ========== 回调方法 ==========

    /**
     * 持续射击时每 tick 调用
     * 用于渲染、音效等
     */
    protected abstract fun onContinuousFireTick(
        level: Level,
        pos: BlockPos,
        target: LivingEntity
    )

    /**
     * 击中目标时调用
     */
    protected open fun onHitTarget(
        level: Level,
        pos: BlockPos,
        target: LivingEntity,
        damage: Float
    ) {
        // 子类可以重写以添加自定义逻辑
    }

    /**
     * 停止射击时调用
     */
    protected open fun onStopFiring() {
        // 子类可以重写以清理状态
    }

    // ========== 数据保存 ==========

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putBoolean("isFiring", isFiring)
        tag.putInt("damageTimer", damageTimer)
        tag.putInt("fireDuration", fireDuration)
        tag.putFloat("totalDamageDealt", totalDamageDealt)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        // 不恢复 isFiring 状态，加载后默认为停止状态
        isFiring = false
        damageTimer = tag.getInt("damageTimer")
        fireDuration = tag.getInt("fireDuration")
        totalDamageDealt = tag.getFloat("totalDamageDealt")
    }

    // ========== 便捷方法 ==========

    /**
     * 获取当前射击强度（基于预热程度）
     */
    fun getFireIntensity(): Float {
        return if (isFiring) warmup else 0f
    }

    /**
     * 获取总射击时间（秒）
     */
    fun getFireDurationSeconds(): Float {
        return fireDuration / 20f
    }

    /**
     * 每秒电力消耗
     */
    fun getPowerConsumptionPerSecond(): Float {
        return powerPerSecond
    }

    /**
     * 每次伤害 tick 的电力消耗
     */
    fun getPowerConsumptionPerDamageTick(): Float {
        return powerPerSecond * damageInterval / 20f
    }

    /**
     * 获取当前激光的 BulletType
     */
    fun getLaserType(): BulletType {
        return shootType
    }
}