package xyz.luobo.mturrets.core.turret.entity

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.max
import kotlin.math.min

/**
 * 带装填逻辑的炮台实体
 * 处理装填计时器、预热、后坐力、热量等
 * 继承自 BaseTurretBlockEntity
 *
 * @param type BlockEntityType
 * @param pos 方块位置
 * @param state 方块状态
 */
abstract class ReloadTurretBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : BaseTurretBlockEntity(type, pos, state) {

    // ========== 装填状态 ==========

    /**
     * 当前装填进度（tick）
     * 当 reloadCounter <= 0 时可以射击
     */
    var reloadCounter: Float = 0f
        protected set

    /**
     * 预热程度（0-1）
     * 某些炮台需要预热才能射击
     */
    var warmup: Float = 0f
        protected set

    /**
     * 后坐力进度（0-1）
     * 用于渲染后坐力动画
     */
    var recoilProgress: Float = 0f
        protected set

    /**
     * 热量值（0-1）
     * 连续射击会积累热量
     */
    var heat: Float = 0f
        protected set

    // ========== 射击状态 ==========

    /**
     * 是否正在射击
     */
    protected var isShooting: Boolean = false

    /**
     * 上次射击时间
     */
    protected var lastShootTime: Long = 0

    // ========== Tick 逻辑 ==========

    override fun tickServer(level: Level, pos: BlockPos, state: BlockState) {
        // 更新装填
        updateReload()

        // 调用父类逻辑
        super.tickServer(level, pos, state)
    }

    // ========== 装填更新 ==========

    /**
     * 更新装填状态
     */
    protected open fun updateReload() {
        // 装填计时器递减
        if (reloadCounter > 0) {
            reloadCounter -= getReloadMultiplier()
            if (reloadCounter < 0) reloadCounter = 0f
        }

        // 预热值处理
        updateWarmup()

        // 后坐力恢复
        if (recoilProgress > 0) {
            recoilProgress -= 1f / max(config.cooldownTime, 1f)
            if (recoilProgress < 0) recoilProgress = 0f
        }

        // 热量冷却
        if (heat > 0) {
            heat -= 1f / max(config.cooldownTime, 1f)
            if (heat < 0) heat = 0f
        }
    }

    /**
     * 更新预热状态
     */
    protected open fun updateWarmup() {
        if (isShooting || (currentTarget != null && reloadCounter <= 0)) {
            warmup = min(1f, warmup + config.shootWarmupSpeed)
        } else {
            warmup = max(0f, warmup - config.shootWarmupSpeed)
        }
    }

    // ========== 射击检查 ==========

    /**
     * 检查是否可以射击
     */
    override fun canShoot(): Boolean {
        return reloadCounter <= 0 &&
                warmup >= config.minWarmup &&
                hasAmmo()
    }

    /**
     * 尝试射击
     */
    override fun tryShoot(level: Level, pos: BlockPos) {
        if (canShoot() && currentTarget != null && canAttack()) {
            performShoot(level, pos, currentTarget!!)
        } else {
            isShooting = false
        }
    }

    /**
     * 执行射击
     */
    protected open fun performShoot(level: Level, pos: BlockPos, target: net.minecraft.world.entity.LivingEntity) {
        isShooting = true

        // 重置装填计时器
        reloadCounter = config.reloadTime

        // 应用后坐力
        recoilProgress = 1f

        // 增加热量
        heat = min(1f, heat + 0.2f)

        // 记录射击时间
        lastShootTime = level.gameTime

        // 发射投射物
        fireProjectile(level, pos, target)

        // 消耗弹药
        consumeAmmo()

        // 触发射击效果
        onShoot(level, pos)

        setChanged()
    }

    /**
     * 射击后的回调
     */
    protected open fun onShoot(level: Level, pos: BlockPos) {
        // 子类可以重写以添加自定义效果
    }

    // ========== 数据保存 ==========

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putFloat("reloadCounter", reloadCounter)
        tag.putFloat("warmup", warmup)
        tag.putFloat("recoilProgress", recoilProgress)
        tag.putFloat("heat", heat)
        tag.putBoolean("isShooting", isShooting)
        tag.putLong("lastShootTime", lastShootTime)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        reloadCounter = tag.getFloat("reloadCounter")
        warmup = tag.getFloat("warmup")
        recoilProgress = tag.getFloat("recoilProgress")
        heat = tag.getFloat("heat")
        isShooting = tag.getBoolean("isShooting")
        lastShootTime = tag.getLong("lastShootTime")
    }

    // ========== 便捷方法 ==========

    /**
     * 获取装填进度百分比（0-1）
     */
    fun getReloadProgress(): Float {
        return 1f - (reloadCounter / config.reloadTime)
    }

    /**
     * 获取预热进度百分比（0-1）
     */
    fun getWarmupProgress(): Float {
        return warmup
    }

    /**
     * 检查是否已完全预热
     */
    fun isFullyWarmedUp(): Boolean {
        return warmup >= 1f
    }

    /**
     * 获取当前实际射速（考虑冷却）
     */
    fun getCurrentFireRate(): Float {
        return config.shotsPerSecond / getReloadMultiplier()
    }
}