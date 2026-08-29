package xyz.luobo.mturrets

import net.neoforged.fml.event.IModBusEvent
import net.neoforged.neoforge.common.ModConfigSpec

object ClientConfig : IModBusEvent {
    val BUILDER: ModConfigSpec.Builder = ModConfigSpec.Builder()
    val SPEC: ModConfigSpec

    init {
        SPEC = BUILDER.build()
    }
}

//object ServerConfig : IModBusEvent { }