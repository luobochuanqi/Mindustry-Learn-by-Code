package xyz.luobo.mturrets.client.audio

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import xyz.luobo.mturrets.common.ModSounds
import xyz.luobo.mturrets.core.machine.HummingMachine

/**
 * 机器运转 hum 的客户端共享控制(#57"抽象公共运行钩子"):窑炉/钻头/(后续)发电机共用。
 * 每机器 BE(按锚点 [BlockPos])作 key,各自独立 [MachineHumSound]。
 *
 * 语义:运行中循环 + 淡入;停摆淡出后引擎自动停通道。再运行时重建实例(旧通道已被引擎摘除)。
 *
 * 驱动面:各机器方块的**客户端** ticker 分支每 tick 调 [tick](发现 + 驱动)。
 * 清理面:方块被破坏/区块卸载后其 ticker 停止、不再 step,残留实例(已 stop 或 BE 脱离)
 * 由 [sweep](全局客户端 tick)摘除,防循环声从旧位置一直响。
 *
 * 无 @OnlyIn(CLIENT):单 NeoForge jar 含全部类,本类仅客户端逻辑;
 * common 的 [HummingMachine] 接口不反向引用它,避免 common 依赖 client。
 */
object MachineHum {
    /** 运行中目标音量(0..1;机器运转声刻意低于枪声)。 */
    private const val TARGET_VOLUME = 0.6f
    /** 淡入/淡出每 tick 步进(≈20 tick 满淡入,贴合 Mindustry SoundLoop fadeSpeed 量级)。 */
    private const val FADE_STEP = 0.05f

    private class Entry(val sound: MachineHumSound, val be: BlockEntity)
    private val active = HashMap<BlockPos, Entry>()

    /** 客户端每 tick(各机器方块客户端 ticker 调用):按运行态起停并逼近目标音量。 */
    fun tick(machine: HummingMachine) {
        val be = machine as BlockEntity
        val pos = be.blockPos
        val mgr = Minecraft.getInstance().soundManager
        val entry = active[pos]
        if (machine.isRunning) {
            // 新机器,或旧实例已淡出停摆(通道已被引擎摘除)/机器已移除 → 重建
            if (entry == null || entry.be.isRemoved || entry.sound.isStopped) {
                if (entry != null) mgr.stop(entry.sound)
                val created = MachineHumSound(
                    ModSounds.MACHINE_HUM.get(),
                    pos.x + 0.5, pos.y + 0.5, pos.z + 0.5,
                    FADE_STEP
                )
                active[pos] = Entry(created, be)
                mgr.play(created)
            } else {
                entry.sound.step(TARGET_VOLUME)
            }
        } else {
            // 停摆:向 0 淡出;淡出完毕由 sound.tick 自行 stop(引擎摘通道),条目留待 sweep 清。
            entry?.sound?.step(0f)
        }
    }

    /** 全局客户端 tick:摘除已停摆或机器 BE 已消失(拆除/卸载)的残留 hum,防循环声泄漏。 */
    fun sweep() {
        val mgr = Minecraft.getInstance().soundManager
        active.entries.removeIf { (_, e) ->
            val dead = e.sound.isStopped || e.be.isRemoved || e.be.level == null
            if (dead && !e.sound.isStopped) mgr.stop(e.sound)
            dead
        }
    }
}
