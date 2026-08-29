package xyz.luobo.mturrets.core.turret.config

/**
  * LEGACY: 翻新期炮台配置表,不迁入新骨架(Builder 与预设工厂为投机配置)。新形状 = TurretSpec 代码表,见 ADR-0009/#28。
 * 炮台静态配置
 * 定义炮台的所有静态属性（类型级别）
 * 模仿 MTurrets 的 Turret 类设计
 *
 * @property identifier 唯一标识符
 * @property description 描述文本
 * @property targetAir 是否对空
 * @property targetGround 是否对地
 * @property targetBlocks 是否对建筑
 * @property playerControllable 玩家是否可控制
 * @property predictTarget 是否预测目标移动
 * @property rotateSpeed 旋转速度（度/tick）
 * @property shootCone 射击角度容差（度）
 * @property range 射程（格）
 * @property minRange 最小射程（格）
 * @property reloadTime 装填时间（tick/shot）
 * @property ammoPerShot 每次射击消耗的弹药
 * @property maxAmmo 最大弹药容量
 * @property cooldownTime 冷却时间（tick）
 * @property inaccuracy 射击散布（度）
 * @property velocityRnd 速度随机性（0-1）
 * @property xRand X轴散布
 * @property recoil 后坐力大小
 * @property shake 屏幕震动
 * @property shootWarmupSpeed 预热速度
 * @property minWarmup 最小预热值（0-1）
 * @property coolantMultiplier 冷却液倍率
 * @property coolantUsage 冷却液消耗
 */
data class TurretConfig(
    // ========== 基础信息 ==========
    val identifier: String,
    val description: String,

    // ========== 目标过滤 ==========
    val targetAir: Boolean = true,
    val targetGround: Boolean = true,
    val targetBlocks: Boolean = false,
    val playerControllable: Boolean = true,
    val predictTarget: Boolean = true,

    // ========== 旋转属性 ==========
    val rotateSpeed: Float = 5f,
    val shootCone: Float = 30f,

    // ========== 射程 ==========
    val range: Float = 20f,
    val minRange: Float = 0f,

    // ========== 装填属性 ==========
    val reloadTime: Float = 20f,
    val ammoPerShot: Int = 1,
    val maxAmmo: Int = 30,
    val cooldownTime: Float = 20f,

    // ========== 射击散布 ==========
    val inaccuracy: Float = 0f,
    val velocityRnd: Float = 0f,
    val xRand: Float = 0f,

    // ========== 视觉效果 ==========
    val recoil: Float = 1f,
    val shake: Float = 0f,
    val shootWarmupSpeed: Float = 0.1f,
    val minWarmup: Float = 0f,

    // ========== 液体冷却 ==========
    val coolantMultiplier: Float = 0.5f,
    val coolantUsage: Float = 0f
) {
    // ========== 计算属性 ==========

    /**
     * 每秒射速
     */
    val shotsPerSecond: Float
        get() = 20f / reloadTime

    /**
     * 冷却 tick 数
     */
    val cooldownTicks: Int
        get() = cooldownTime.toInt()

    /**
     * 装填 tick 数
     */
    val reloadTicks: Int
        get() = reloadTime.toInt()

    /**
     * 是否有最小射程限制
     */
    val hasMinRange: Boolean
        get() = minRange > 0f

    /**
     * 是否有散布
     */
    val hasInaccuracy: Boolean
        get() = inaccuracy > 0f

    /**
     * 是否需要预热
     */
    val requiresWarmup: Boolean
        get() = minWarmup > 0f

    /**
     * 是否支持液体冷却
     */
    val supportsCoolant: Boolean
        get() = coolantMultiplier < 1f && coolantUsage > 0f

    // ========== Builder 模式 ==========

    class Builder(
        private val identifier: String,
        private val description: String
    ) {
        private var targetAir: Boolean = true
        private var targetGround: Boolean = true
        private var targetBlocks: Boolean = false
        private var playerControllable: Boolean = true
        private var predictTarget: Boolean = true
        private var rotateSpeed: Float = 5f
        private var shootCone: Float = 30f
        private var range: Float = 20f
        private var minRange: Float = 0f
        private var reloadTime: Float = 20f
        private var ammoPerShot: Int = 1
        private var maxAmmo: Int = 30
        private var cooldownTime: Float = 20f
        private var inaccuracy: Float = 0f
        private var velocityRnd: Float = 0f
        private var xRand: Float = 0f
        private var recoil: Float = 1f
        private var shake: Float = 0f
        private var shootWarmupSpeed: Float = 0.1f
        private var minWarmup: Float = 0f
        private var coolantMultiplier: Float = 0.5f
        private var coolantUsage: Float = 0f

        fun targetAir(value: Boolean) = apply { this.targetAir = value }
        fun targetGround(value: Boolean) = apply { this.targetGround = value }
        fun targetBlocks(value: Boolean) = apply { this.targetBlocks = value }
        fun playerControllable(value: Boolean) = apply { this.playerControllable = value }
        fun predictTarget(value: Boolean) = apply { this.predictTarget = value }
        fun rotateSpeed(value: Float) = apply { this.rotateSpeed = value }
        fun shootCone(value: Float) = apply { this.shootCone = value }
        fun range(value: Float) = apply { this.range = value }
        fun minRange(value: Float) = apply { this.minRange = value }
        fun reloadTime(value: Float) = apply { this.reloadTime = value }
        fun ammoPerShot(value: Int) = apply { this.ammoPerShot = value }
        fun maxAmmo(value: Int) = apply { this.maxAmmo = value }
        fun cooldownTime(value: Float) = apply { this.cooldownTime = value }
        fun inaccuracy(value: Float) = apply { this.inaccuracy = value }
        fun velocityRnd(value: Float) = apply { this.velocityRnd = value }
        fun xRand(value: Float) = apply { this.xRand = value }
        fun recoil(value: Float) = apply { this.recoil = value }
        fun shake(value: Float) = apply { this.shake = value }
        fun shootWarmupSpeed(value: Float) = apply { this.shootWarmupSpeed = value }
        fun minWarmup(value: Float) = apply { this.minWarmup = value }
        fun coolantMultiplier(value: Float) = apply { this.coolantMultiplier = value }
        fun coolantUsage(value: Float) = apply { this.coolantUsage = value }

        fun build(): TurretConfig = TurretConfig(
            identifier = identifier,
            description = description,
            targetAir = targetAir,
            targetGround = targetGround,
            targetBlocks = targetBlocks,
            playerControllable = playerControllable,
            predictTarget = predictTarget,
            rotateSpeed = rotateSpeed,
            shootCone = shootCone,
            range = range,
            minRange = minRange,
            reloadTime = reloadTime,
            ammoPerShot = ammoPerShot,
            maxAmmo = maxAmmo,
            cooldownTime = cooldownTime,
            inaccuracy = inaccuracy,
            velocityRnd = velocityRnd,
            xRand = xRand,
            recoil = recoil,
            shake = shake,
            shootWarmupSpeed = shootWarmupSpeed,
            minWarmup = minWarmup,
            coolantMultiplier = coolantMultiplier,
            coolantUsage = coolantUsage
        )
    }

    companion object {
        /**
         * 创建配置构建器
         */
        fun builder(identifier: String, description: String) =
            Builder(identifier, description)

        /**
         * 基础炮台配置
         */
        fun basic(identifier: String, description: String) = TurretConfig(
            identifier = identifier,
            description = description
        )

        /**
         * 防空炮台配置
         */
        fun antiAir(identifier: String, description: String) = TurretConfig(
            identifier = identifier,
            description = description,
            targetAir = true,
            targetGround = false,
            rotateSpeed = 8f,
            inaccuracy = 5f
        )

        /**
         * 对地炮台配置
         */
        fun antiGround(identifier: String, description: String) = TurretConfig(
            identifier = identifier,
            description = description,
            targetAir = false,
            targetGround = true,
            rotateSpeed = 4f,
            inaccuracy = 2f
        )

        /**
         * 激光炮台配置
         */
        fun laser(identifier: String, description: String) = TurretConfig(
            identifier = identifier,
            description = description,
            rotateSpeed = 3f,
            shootCone = 5f,
            inaccuracy = 0f,
            shootWarmupSpeed = 0.05f
        )

        /**
         * 狙击炮台配置
         */
        fun sniper(identifier: String, description: String) = TurretConfig(
            identifier = identifier,
            description = description,
            range = 40f,
            reloadTime = 60f,
            rotateSpeed = 2f,
            inaccuracy = 0f,
            shootCone = 2f,
            shake = 2f
        )

        /**
         * 速射炮台配置
         */
        fun rapidFire(identifier: String, description: String) = TurretConfig(
            identifier = identifier,
            description = description,
            reloadTime = 5f,
            rotateSpeed = 10f,
            inaccuracy = 3f,
            maxAmmo = 60,
            cooldownTime = 10f
        )

        /**
         * 火炮/榴弹炮配置
         */
        fun artillery(identifier: String, description: String) = TurretConfig(
            identifier = identifier,
            description = description,
            range = 30f,
            minRange = 5f,
            reloadTime = 40f,
            rotateSpeed = 2f,
            inaccuracy = 1f,
            velocityRnd = 0.2f,
            shake = 3f
        )
    }
}