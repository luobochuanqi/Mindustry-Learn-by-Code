package xyz.luobo.mturrets.client.audio

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource

/**
 * 机器运转循环嗡鸣(#57):客户端 looping [AbstractTickableSoundInstance],
 * 对齐 Mindustry [SoundLoop] 与 Create AirCurrentSound 的"运行中循环、停摆淡出"语义。
 *
 * 引擎对 ticking sound 每 tick 重采样 [getVolume](SoundEngine 逐 tick setVolume),
 * 而 getVolume = volume 字段 × 每文件音量(sounds.json 未设 → 1.0),故直接改继承的
 * [volume] 字段即淡入/淡出(同 Create AirCurrentSound)。volume=0 起步需 [canStartSilent]=true
 * (否则引擎把 0 音量当"跳过"不建通道)。
 *
 * 淡出到 0 置 [settled](仅"曾非零、现归零"时,区分"新生成@0"与"淡出完毕@0");
 * [tick](引擎每 tick 调用)见 settled 即 [stop] 置 isStopped,引擎据此摘除通道与 ticking 条目。
 *
 * 无 @OnlyIn(CLIENT):单 NeoForge jar 含全部类,本类仅客户端实例化/调用;
 * 客户端驱动隔离在方块客户端 ticker 与 [MachineHum]。
 */
class MachineHumSound(
    soundEvent: SoundEvent,
    x: Double,
    y: Double,
    z: Double,
    /** 每 tick 逼近增量(淡入/淡出速率)。 */
    private val step: Float
) : AbstractTickableSoundInstance(soundEvent, SoundSource.BLOCKS, RandomSource.create()) {

    private var settled = false

    init {
        looping = true
        delay = 0
        relative = false
        volume = 0f
        this.x = x
        this.y = y
        this.z = z
    }

    /** 向 [target] 逼近一步(运行中 target 为运行音量,停摆时传 0 使其淡出)。 */
    fun step(target: Float) {
        val wasNonZero = volume > 0f
        volume = if (volume < target) (volume + step).coerceAtMost(target)
        else (volume - step).coerceAtLeast(0f)
        if (wasNonZero && volume <= 0f) settled = true
    }

    /** 引擎每 tick 调用:淡出完毕即自行 stop(置 isStopped 供引擎摘除通道与 ticking 条目)。 */
    override fun tick() {
        if (settled) stop()
    }

    override fun canStartSilent(): Boolean = true
}
