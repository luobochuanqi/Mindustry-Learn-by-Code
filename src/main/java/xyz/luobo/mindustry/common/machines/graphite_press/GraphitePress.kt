package xyz.luobo.mindustry.common.machines.graphite_press

import net.minecraft.resources.ResourceLocation
import xyz.luobo.mindustry.Mindustry
import xyz.luobo.mindustry.core.registry.BlockRegistry
import xyz.luobo.mindustry.core.registry.MachineDefinition
import xyz.luobo.mindustry.core.registry.MachineRegistry

object GraphitePress {
//    val ID = ResourceLocation(Mindustry.MOD_ID, "graphite_press")

    fun init() {
        MachineRegistry.register(
            ID, MachineDefinition(
                size = 2, // 2x2 方块
                controllerBlock = { GraphitePressBlock() },
                blockEntity = { pos, state -> GraphitePressBlockEntity(pos, state) },
                texture = ResourceLocation(Mindustry.MOD_ID, "block/graphite_press"),
                energyCapacity = 5000,
                inventorySlots = 3,
                logic = GraphitePressLogic
            )
        )

        // 自动注册方块/物品
        BlockRegistry.registerController(ID)
        BlockRegistry.registerDummy(ID)
    }
}