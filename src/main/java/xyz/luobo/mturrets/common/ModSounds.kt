package xyz.luobo.mturrets.common

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import xyz.luobo.mturrets.MTurrets
import java.util.function.Supplier

/**
 * 声音事件(#57):3 个 SoundEvent,逐一对应 assets/mturrets/sounds/ 下的 ogg。
 * 枪声按炮台分(Duo/Scatter,对齐上游 shootDuo/shootScatter);机器运转用单一共享 hum
 * (loopMachine,按机器类型细分 loopDrill/loopSmelter 推后,见 #57 spec)。
 * 事件为 data-driven(1.21.1 SoundEvent 是 registry 数据),客户端由 sounds.json 关联音源文件;
 * sounds.json 由 datagen 生成(ModSoundProvider),CI 以 git diff 把关。
 */
object ModSounds {
    private val SOUNDS: DeferredRegister<SoundEvent> =
        DeferredRegister.create(Registries.SOUND_EVENT, MTurrets.MOD_ID)

    private fun event(name: String): DeferredHolder<SoundEvent, SoundEvent> {
        val location = ResourceLocation.fromNamespaceAndPath(MTurrets.MOD_ID, name)
        val supplier: Supplier<SoundEvent> = { SoundEvent.createVariableRangeEvent(location) }
        return SOUNDS.register(name, supplier)
    }

    /** Duo 枪声(shot 单发)。 */
    val SHOOT_DUO: DeferredHolder<SoundEvent, SoundEvent> = event("shoot_duo")
    /** Scatter 枪声(点射两发各响一次)。 */
    val SHOOT_SCATTER: DeferredHolder<SoundEvent, SoundEvent> = event("shoot_scatter")
    /** 机器运转共享循环嗡鸣(窑炉/钻头/后续发电机复用;客户端 looping 起停)。 */
    val MACHINE_HUM: DeferredHolder<SoundEvent, SoundEvent> = event("machine_hum")

    fun register() {
        SOUNDS.register(MOD_BUS)
    }
}
