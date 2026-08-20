package xyz.luobo.mturrets.core.turret.logic

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

/**
 * 提前量计算器
 * 计算射击提前量以命中移动目标
 * 基于 MTurrets 的提前量计算算法
 */
object LeadCalculator {

    /**
     * 提前量计算结果
     */
    data class LeadResult(
        /** 预测的命中位置 */
        val predictedPosition: Vec3,
        /** 所需的射击方向（归一化） */
        val aimDirection: Vec3,
        /** 预计命中时间（tick） */
        val timeToHit: Double,
        /** 是否可以命中 */
        val canHit: Boolean,
        /** 命中率估计（0-1） */
        val hitProbability: Double = 1.0
    )

    /**
     * 计算提前量
     *
     * @param shooterPos 射击者位置
     * @param target 目标实体
     * @param projectileSpeed 弹丸速度
     * @param inaccuracy 误差角度（度）
     * @param maxIterations 最大迭代次数
     * @return 提前量计算结果
     */
    fun calculateLead(
        shooterPos: Vec3,
        target: LivingEntity,
        projectileSpeed: Double,
        inaccuracy: Double = 0.0,
        maxIterations: Int = 5
    ): LeadResult {
        // 获取目标位置和速度
        val targetPos = target.position()
        val targetVel = target.deltaMovement

        // 初始猜测：目标当前位置
        var predictedPos = targetPos
        var timeToHit = 0.0

        // 迭代优化预测位置
        for (i in 0 until maxIterations) {
            // 计算到预测位置的距离和方向
            val displacement = predictedPos.subtract(shooterPos)
            val distance = displacement.length()

            // 计算到达预测位置所需时间
            timeToHit = if (projectileSpeed > 0) {
                distance / projectileSpeed
            } else {
                0.0
            }

            // 预测目标在时间后的位置
            // 考虑目标的速度和加速度（简化模型）
            val newPredictedPos = targetPos.add(targetVel.scale(timeToHit))

            // 如果预测位置收敛，退出循环
            if (newPredictedPos.distanceToSqr(predictedPos) < 0.01) {
                break
            }

            predictedPos = newPredictedPos
        }

        // 计算射击方向
        val aimDir = predictedPos.subtract(shooterPos).normalize()

        // 检查是否可以命中（速度是否足够）
        val canHit = if (projectileSpeed > 0) {
            val distance = predictedPos.distanceTo(shooterPos)
            val timeRequired = distance / projectileSpeed

            // 如果弹丸寿命有限，检查是否能在寿命内到达
            // 这里简化处理，假设寿命足够
            timeRequired < 100  // 最大5秒
        } else {
            true  // 瞬间命中
        }

        // 计算命中率估计
        val hitProbability = calculateHitProbability(
            targetVel,
            projectileSpeed,
            inaccuracy,
            timeToHit
        )

        return LeadResult(
            predictedPosition = predictedPos,
            aimDirection = aimDir,
            timeToHit = timeToHit,
            canHit = canHit,
            hitProbability = hitProbability
        )
    }

    /**
     * 简化的提前量计算（不考虑迭代优化）
     *
     * @param shooterPos 射击者位置
     * @param targetPos 目标当前位置
     * @param targetVel 目标速度
     * @param projectileSpeed 弹丸速度
     * @return 预测的目标位置
     */
    fun calculateSimpleLead(
        shooterPos: Vec3,
        targetPos: Vec3,
        targetVel: Vec3,
        projectileSpeed: Double
    ): Vec3 {
        if (projectileSpeed <= 0) {
            return targetPos  // 瞬间命中，不需要提前量
        }

        // 计算目标相对位置
        val relativePos = targetPos.subtract(shooterPos)
        val distance = relativePos.length()

        // 计算到达时间
        val timeToHit = distance / projectileSpeed

        // 预测目标位置
        return targetPos.add(targetVel.scale(timeToHit))
    }

    /**
     * 解析提前量方程
     * 解二次方程：|P + V*t| = S*t
     * 其中 P 是相对位置，V 是目标速度，S 是弹丸速度，t 是时间
     *
     * @param relativePos 目标相对位置
     * @param targetVel 目标速度
     * @param projectileSpeed 弹丸速度
     * @return 命中时间（负数表示无法命中）
     */
    fun solveLeadEquation(
        relativePos: Vec3,
        targetVel: Vec3,
        projectileSpeed: Double
    ): Double {
        // 计算系数
        val a = targetVel.lengthSqr() - projectileSpeed * projectileSpeed
        val b = 2 * relativePos.dot(targetVel)
        val c = relativePos.lengthSqr()

        // 解二次方程
        return when {
            kotlin.math.abs(a) < 1e-6 -> {
                // 线性方程（目标速度与弹丸速度相同）
                if (kotlin.math.abs(b) < 1e-6) -1.0 else -c / b
            }

            else -> {
                val discriminant = b * b - 4 * a * c
                if (discriminant < 0) {
                    // 无解，无法命中
                    -1.0
                } else {
                    val sqrtDisc = sqrt(discriminant)
                    val t1 = (-b + sqrtDisc) / (2 * a)
                    val t2 = (-b - sqrtDisc) / (2 * a)

                    // 选择正的、较小的时间
                    val validTimes = listOf(t1, t2).filter { it > 0 }
                    if (validTimes.isEmpty()) -1.0 else validTimes.minOrNull()!!
                }
            }
        }
    }

    /**
     * 计算命中率估计
     *
     * @param targetVel 目标速度
     * @param projectileSpeed 弹丸速度
     * @param inaccuracy 误差角度（度）
     * @param timeToHit 预计命中时间
     * @return 命中率（0-1）
     */
    private fun calculateHitProbability(
        targetVel: Vec3,
        projectileSpeed: Double,
        inaccuracy: Double,
        timeToHit: Double
    ): Double {
        // 速度比
        val speedRatio = targetVel.length() / projectileSpeed

        // 目标速度越快，命中率越低
        val speedFactor = kotlin.math.max(0.0, 1.0 - speedRatio * 0.5)

        // 误差越大，命中率越低
        val accuracyFactor = kotlin.math.max(0.0, 1.0 - inaccuracy / 90.0)

        // 时间越长，不确定性越大
        val timeFactor = kotlin.math.max(0.0, 1.0 - timeToHit / 100.0)

        return speedFactor * accuracyFactor * timeFactor
    }

    /**
     * 计算最优射击角度
     *
     * @param shooterPos 射击者位置
     * @param target 目标实体
     * @param projectileSpeed 弹丸速度
     * @return 最优射击方向（归一化向量）
     */
    fun calculateOptimalAim(
        shooterPos: Vec3,
        target: LivingEntity,
        projectileSpeed: Double
    ): Vec3 {
        val result = calculateLead(shooterPos, target, projectileSpeed)
        return result.aimDirection
    }

    /**
     * 检查目标是否在做机动（难以预测）
     *
     * @param target 目标实体
     * @param history 历史位置记录
     * @return 是否在做机动
     */
    fun isEvasiveManeuvering(
        target: LivingEntity,
        history: List<Vec3>
    ): Boolean {
        if (history.size < 3) return false

        // 计算速度变化
        val velocities = mutableListOf<Vec3>()
        for (i in 1 until history.size) {
            velocities.add(history[i].subtract(history[i - 1]))
        }

        // 计算加速度变化
        var accelerationChange = 0.0
        for (i in 1 until velocities.size) {
            val accel = velocities[i].subtract(velocities[i - 1])
            accelerationChange += accel.length()
        }

        // 如果加速度变化大，认为在做机动
        return accelerationChange > 0.1
    }

    /**
     * 预测目标在未来一段时间内的位置范围
     *
     * @param target 目标实体
     * @param timeTicks 预测时间（tick）
     * @return 可能的位置范围（以预测位置为中心的球体半径）
     */
    fun predictPositionUncertainty(
        target: LivingEntity,
        timeTicks: Double
    ): Double {
        val velocity = target.deltaMovement
        val speed = velocity.length()

        // 基础不确定性随时间线性增长
        var uncertainty = 0.1 * timeTicks

        // 速度越快，不确定性越大
        uncertainty += speed * timeTicks * 0.01

        // 如果目标在空中，增加额外不确定性
        if (!target.onGround()) {
            uncertainty *= 1.5
        }

        return uncertainty
    }
}