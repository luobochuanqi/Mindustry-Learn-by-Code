package xyz.luobo.mindustry.core.turret

import net.minecraft.world.level.block.BaseEntityBlock

abstract class BaseTurretBlock<T : BaseTurretBE>(
    properties: Properties
) : BaseEntityBlock(properties) {

}