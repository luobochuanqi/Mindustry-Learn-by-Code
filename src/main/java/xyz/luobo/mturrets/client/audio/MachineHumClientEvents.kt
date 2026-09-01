package xyz.luobo.mturrets.client.audio

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import xyz.luobo.mturrets.MTurrets

/**
 * 机器运转 hum 的全局客户端 tick(#57):每 tick 调 [MachineHum.sweep] 摘除已停摆或机器 BE
 * 已消失(拆除/区块卸载)的残留循环声,防从旧位置一直响。
 *
 * 走**游戏**事件总线(ClientTickEvent,非 mod 总线),故不 extends IModBusEvent。
 * 各机器方块的客户端 ticker 负责"发现 + 驱动"([MachineHum.tick]),本订阅器只兜底清理。
 */
@EventBusSubscriber(modid = MTurrets.MOD_ID, value = [Dist.CLIENT])
object MachineHumClientEvents {
    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        MachineHum.sweep()
    }
}
