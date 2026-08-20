package xyz.luobo.mturrets

import net.neoforged.fml.event.IModBusEvent
import net.neoforged.neoforge.common.ModConfigSpec

object ClientConfig : IModBusEvent {
    val BUILDER: ModConfigSpec.Builder = ModConfigSpec.Builder()
    val SPEC: ModConfigSpec

    var maxRenderDistance: ModConfigSpec.ConfigValue<Int>

    init {
        maxRenderDistance = BUILDER
            .comment("Max render distance for power laser")
            .define("maxRenderDistance", 64)

        SPEC = BUILDER.build()
    }
}

//object ServerConfig : IModBusEvent { }