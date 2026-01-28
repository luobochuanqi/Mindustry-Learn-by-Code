package xyz.luobo.mindustry.core.multiblock

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB

interface MultiblockComponent {
    // 结构状态
    val isFormed: Boolean
    val controllerPos: BlockPos?

    // TODO("分长宽高")
    val structureSize: Int

    // 生命周期
    fun attemptForm(level: Level, pos: BlockPos): Boolean
    fun onStructureFormed()
    fun onStructureBroken()

    // 验证
    fun validateStructure(level: Level, pos: BlockPos): Boolean

    // 性能优化
    fun getCachedStructureBounds(): AABB?
    fun clearCache()
}