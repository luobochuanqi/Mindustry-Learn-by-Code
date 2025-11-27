package xyz.luobo.mindustry.common.machines.graphite_press

import net.minecraft.resources.ResourceLocation
import xyz.luobo.mindustry.Mindustry
import xyz.luobo.mindustry.core.machine.IMachine
import xyz.luobo.mindustry.core.registry.MachineDefinition
import xyz.luobo.mindustry.core.registry.MachineRegistry

object GraphitePress : IMachine {

    // 统一资源表示符
    val ID = ResourceLocation.fromNamespaceAndPath(Mindustry.MOD_ID, "graphite_press")

    fun init() {
        MachineRegistry.register(
            ID, MachineDefinition(
                size = 2, // 2x2x2 cube
                controllerBlock = { GraphitePressBlock() },
                energyCapacity = 5000,
                inventorySlots = 3,
                logic = GraphitePressLogic
            )
        )
    }

    override fun getMachineDefinition(): MachineDefinition {
        TODO("Not yet implemented")
    }

    override fun getMachineID(): ResourceLocation {
        TODO("Not yet implemented")
    }

    override fun registerMachine() {
        TODO("Not yet implemented")
    }
}