package xyz.luobo.mindustry

import net.neoforged.fml.event.IModBusEvent
import net.neoforged.neoforge.common.ModConfigSpec

object Config : IModBusEvent {
    val BUILDER: ModConfigSpec.Builder = ModConfigSpec.Builder()
    val SPEC: ModConfigSpec

    var isDebugMode: ModConfigSpec.BooleanValue

    init {
        isDebugMode = BUILDER
            .comment("Is Debug Mode?")
            .define("isDebugMode", false)

        SPEC = BUILDER.build()
    }
}

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