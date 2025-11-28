package xyz.luobo.mindustry.common.machines.graphite_press

import net.minecraft.resources.ResourceLocation
import xyz.luobo.mindustry.Mindustry
import xyz.luobo.mindustry.core.machine.IMachine
import xyz.luobo.mindustry.core.registry.MachineDefinition

object GraphitePress : IMachine {

    // 统一资源表示符
    val MACHINE_ID = ResourceLocation.fromNamespaceAndPath(Mindustry.MOD_ID, "graphite_press")

    override fun getMachineDefinition(): MachineDefinition {
        return MachineDefinition(
            size = 2, // 2x2x2 cube
            controllerBlock = { GraphitePressBlock() },
            block = { GraphitePressDummyBlock() },
            energyCapacity = 5000,
            inventorySlots = 3,
            logic = GraphitePressLogic
        )
    }

    override fun getMachineID(): ResourceLocation {
        return MACHINE_ID
    }

    override fun registerMachine() {
        TODO("Not yet implemented")
    }
}