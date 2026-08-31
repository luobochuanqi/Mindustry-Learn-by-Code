package xyz.luobo.mturrets.core.combat

import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

/**
 * 提前量解算(存活件,ADR-0009):对位原版 Predict.intercept。
 * legacy 框架清理时只保留 TurretBE.aimPoint 消费的解析解,迭代/机动/不确定度等投机分支随旧包一并删除。
 */
object LeadCalculator {

    /**
     * 解二次方程 |P + V·t| = S·t 求命中时间(P 相对位置,V 目标速度,S 弹速)。
     * @return 命中时间(tick);负数表示无法命中
     */
    fun solveLeadEquation(
        relativePos: Vec3,
        targetVel: Vec3,
        projectileSpeed: Double
    ): Double {
        val a = targetVel.lengthSqr() - projectileSpeed * projectileSpeed
        val b = 2 * relativePos.dot(targetVel)
        val c = relativePos.lengthSqr()

        return when {
            kotlin.math.abs(a) < 1e-6 -> {
                // 线性方程(目标速度与弹丸速度相同)
                if (kotlin.math.abs(b) < 1e-6) -1.0 else -c / b
            }

            else -> {
                val discriminant = b * b - 4 * a * c
                if (discriminant < 0) {
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
}
