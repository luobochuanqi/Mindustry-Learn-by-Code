package xyz.luobo.mturrets.common

import net.minecraft.server.level.ServerLevel
import net.neoforged.neoforge.network.PacketDistributor

/**
 * 发送是服务端在命中/到寿处对命中位置附近 64 格内玩家广播(视觉-only,粒子视距 = 64 格)。
 * 一期玩家规模小;若后续需更精细裁剪,可动态半径或按 chunk 追踪。
 */
object Payloads {
    /** 服务端对命中点附近 64 格内玩家广播 FX payload(命中/到寿时调用);纯视觉,按距离裁剪。 */
    fun send(level: ServerLevel, payload: BulletFxPayload) {
        PacketDistributor.sendToPlayersNear(level, null, payload.pos.x, payload.pos.y, payload.pos.z, 64.0, payload)
    }
}
