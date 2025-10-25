package xyz.luobo.mindustry

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.IModBusEvent
import net.neoforged.fml.event.config.ModConfigEvent
import net.neoforged.neoforge.common.ModConfigSpec

@EventBusSubscriber(modid = Mindustry.MOD_ID)
object Config: IModBusEvent {
    val BUILDER: ModConfigSpec.Builder = ModConfigSpec.Builder()

    val IS_DEBUG_MODE: ModConfigSpec.BooleanValue = BUILDER.comment("Is Debug Mode?")
        .define("isDebugMode", true)

    var isDebugMode: Boolean = false

    @SubscribeEvent
    fun onLoad(event: ModConfigEvent?) {
        isDebugMode = IS_DEBUG_MODE.get()
    }
}