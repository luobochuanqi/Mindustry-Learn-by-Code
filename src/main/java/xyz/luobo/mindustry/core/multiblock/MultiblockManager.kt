package xyz.luobo.mindustry.core.multiblock

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mindustry.core.registry.MachineRegistry

/**
 * 管理多方块结构的生命周期
 * 包含在破坏方块后自动清除所有多方块结构的子方块的逻辑
 * 在放置一个多方块的控制器后自动生成所有子方块的逻辑
 */
class MultiblockManager {
    companion object {
        const val MAX_MULTIBLOCK_SIZE = 16
    }

    /**
     * 尝试形成多方块结构
     * @param level 世界
     * @param controllerPos 控制器位置
     * @param size 多方块结构大小
     * @return 是否成功形成结构
     */
    fun attemptFormStructure(level: Level, controllerPos: BlockPos, size: Int): Boolean {
        if (level.isClientSide) return false

        val controllerState = level.getBlockState(controllerPos)
        val controllerEntity = level.getBlockEntity(controllerPos)

        // 检查是否是有效的多方块控制器
        if (controllerEntity !is IMultiblockControllerBlockEntity) return false

        // 清理已存在的结构
        breakStructure(level, controllerPos, size)

        // 检查是否能形成结构
        if (!checkStructure(level, controllerPos, size, controllerState)) {
            return false
        }

        // 创建多方块结构
        formStructure(level, controllerPos, size)
        return true
    }

    /**
     * 检查多方块结构是否有效
     * @param level 世界
     * @param controllerPos 控制器位置
     * @param size 多方块结构大小
     * @param controllerState 控制器方块状态
     * @return 结构是否有效
     */
    private fun checkStructure(level: Level, controllerPos: BlockPos, size: Int, controllerState: BlockState): Boolean {
        // 检查范围
        if (size > MAX_MULTIBLOCK_SIZE || size <= 0) return false

        // 检查每个位置是否可以放置多方块部件
        for (x in 0 until size) {
            for (y in 0 until size) {
                for (z in 0 until size) {
                    // 控制器位置跳过
                    if (x == 0 && y == 0 && z == 0) continue

                    val partPos = controllerPos.offset(x, y, z)
                    val partState = level.getBlockState(partPos)

                    // 检查是否是空气方块或者可以替换的方块
                    if (!partState.isAir && !partState.canBeReplaced()) {
                        return false
                    }
                }
            }
        }
        return true
    }

    /**
     * 形成多方块结构
     * @param level 世界
     * @param controllerPos 控制器位置
     * @param size 多方块结构大小
     */
    private fun formStructure(level: Level, controllerPos: BlockPos, size: Int) {
        val machineDef = MachineRegistry.getDefinitionByBlock(level.getBlockState(controllerPos).block) ?: return

        for (x in 0 until size) {
            for (y in 0 until size) {
                for (z in 0 until size) {
                    // 控制器位置跳过
                    if (x == 0 && y == 0 && z == 0) continue

                    val partPos = controllerPos.offset(x, y, z)
                    val partState = machineDef.block().defaultBlockState()

                    level.setBlock(partPos, partState, 3)

                    // 更新方块实体信息，存储控制器位置
                    val partEntity = level.getBlockEntity(partPos)
                    if (partEntity is MultiblockDummyBlockEntity) {
                        partEntity.setControllerPos(controllerPos)
                    }
                }
            }
        }
    }

    /**
     * 破坏多方块结构
     * @param level 世界
     * @param controllerPos 控制器位置
     * @param size 多方块结构大小
     */
    fun breakStructure(level: Level, controllerPos: BlockPos, size: Int) {
        if (level.isClientSide) return

        for (x in 0 until size) {
            for (y in 0 until size) {
                for (z in 0 until size) {
                    // 控制器位置跳过
                    if (x == 0 && y == 0 && z == 0) continue

                    val partPos = controllerPos.offset(x, y, z)
                    val partState = level.getBlockState(partPos)

                    // 检查是否是多方块部件
                    if (partState.block is MultiblockDummyBlock) {
                        level.removeBlock(partPos, false)
                    }
                }
            }
        }
    }

    /**
     * 查找控制器位置
     * @param level 世界
     * @param partPos 部件位置
     * @return 控制器位置，如果未找到则返回null
     */
    fun findController(level: Level, partPos: BlockPos): BlockPos? {
        val dummyEntity = level.getBlockEntity(partPos)
        if (dummyEntity is MultiblockDummyBlockEntity) {
            return dummyEntity.getControllerPos()
        }
        return null
    }
}