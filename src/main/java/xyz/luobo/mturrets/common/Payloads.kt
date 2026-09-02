package xyz.luobo.mturrets.common

import net.minecraft.server.level.ServerLevel
import net.neoforged.neoforge.network.PacketDistributor

/**
 * 自定义 payload 发送(#62)。注册在 [EventHandler] 的客户端/服务端
 * [net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent] 回调里;
 * 发送是服务端在命中/到寿处对整服玩家广播(视觉-only,一期玩家规模小;
 * 若后续需按视距裁剪,改用 sendToPlayersTrackingArea)。
 */
object Payloads {
    /** 服务端对同维玩家广播 FX payload(命中/到寿时调用);纯视觉,按维度裁剪(不同维看不到该炮台)。 */
    fun send(level: ServerLevel, payload: BulletFxPayload) {
        PacketDistributor.sendToPlayersInDimension(level, payload)
    }
}
