package xyz.luobo.mindustry.core.multiblock

import IMultiblockController
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluids

class MultiblockManager {
    /**
     * 检查指定位置是否可以放置多方块结构
     *
     * @param level 当前游戏世界等级对象
     * @param pos 结构放置的起始位置坐标
     * @param controller 多方块结构控制器实例
     * @param size 结构的尺寸大小
     * @return 如果可以在指定位置放置结构则返回true，否则返回false
     */
    fun canPlaceStructure(level: Level, pos: BlockPos, controller: IMultiblockController, size: Int): Boolean {
        // 验证基础参数有效性[citation:4][citation:8]
        if (level.isClientSide) return false // 仅在服务器端执行
        if (size <= 0) return false // 尺寸必须为正数

        // 获取多方块结构的相对位置模式[citation:7]
        val structurePattern = controller.getStructurePattern(size)
        val worldBounds = getWorldBounds(level)

        // 检查结构是否在世界边界内[citation:7]
        if (!isStructureWithinBounds(pos, structurePattern, worldBounds)) {
            return false
        }

        // 检查每个结构方块的放置条件
        for (relativePos in structurePattern) {
            val worldPos = pos.offset(relativePos)

            if (!isValidBlockPosition(level, worldPos, controller, relativePos)) {
                return false
            }
        }

        // 检查结构完整性（如控制器唯一性等）[citation:7]
        if (!validateStructureIntegrity(level, pos, controller, structurePattern)) {
            return false
        }

        return true
    }


    /**
     * 检查单个方块位置是否有效
     *
     * @param level 当前游戏世界等级对象
     * @param worldPos 要检查的世界坐标位置
     * @param controller 多方块结构控制器
     * @param relativePos 相对于多方块结构的相对坐标位置
     * @return 如果该位置可以放置多方块结构的方块则返回true，否则返回false
     */
    private fun isValidBlockPosition(
        level: Level,
        worldPos: BlockPos,
        controller: IMultiblockController,
        relativePos: Vec3i
    ): Boolean {
        val currentState = level.getBlockState(worldPos)

        // 检查位置是否在世界边界范围内
        if (!level.isInWorldBounds(worldPos)) return false

        // 验证方块是否可以被替换
        if (!canReplaceBlock(currentState, level, worldPos)) {
            return false
        }

        // 验证该位置是否满足控制器对指定位置的方块要求
        if (!controller.isValidBlockForPosition(level, worldPos, relativePos)) {
            return false
        }

        return true
    }


    /**
     * 判断方块是否可被替换
     *
     * @param state 方块状态
     * @param level 世界等级对象
     * @param pos 方块位置
     * @return 如果方块可以被替换则返回true，否则返回false
     */
    private fun canReplaceBlock(state: BlockState, level: Level, pos: BlockPos): Boolean {
        val block = state.block

        // 可替换的方块类型
        return state.isAir || // 空气
                state.fluidState.type == Fluids.FLOWING_WATER || // 流动水
                state.fluidState.type == Fluids.FLOWING_LAVA || // 流动岩浆
//                state.material.isReplaceable || // 可替换材料（如雪、草等）
                block.defaultDestroyTime() <= 0.5f // 易破坏的方块（如花、火把等）
    }


    /**
     * 检查整个结构是否在世界边界内
     *
     * @param centerPos 结构的中心位置
     * @param structurePattern 结构的相对位置模式列表
     * @param worldBounds 世界边界范围，包含最小和最大坐标点
     * @return 如果整个结构都在世界边界内则返回true，否则返回false
     */
    private fun isStructureWithinBounds(
        centerPos: BlockPos,
        structurePattern: List<Vec3i>,
        worldBounds: Pair<BlockPos, BlockPos>
    ): Boolean {
        val (minBound, maxBound) = worldBounds

        // 遍历结构中的每个相对位置，检查其对应的世界坐标是否超出边界
        for (relativePos in structurePattern) {
            val worldPos = centerPos.offset(relativePos)

            if (worldPos.x < minBound.x || worldPos.x > maxBound.x ||
                worldPos.y < minBound.y || worldPos.y > maxBound.y ||
                worldPos.z < minBound.z || worldPos.z > maxBound.z
            ) {
                return false
            }
        }
        return true
    }


    /**
     * 验证结构完整性规则[citation:7]
     *
     * @param level 当前世界等级对象，用于访问方块信息
     * @param pos 多方块结构的基准位置
     * @param controller 多方块控制器实例，负责控制结构逻辑
     * @param structurePattern 结构模式定义，包含相对坐标的偏移量列表
     * @return 如果结构完整性验证通过返回true，否则返回false
     */
    private fun validateStructureIntegrity(
        level: Level,
        pos: BlockPos,
        controller: IMultiblockController,
        structurePattern: List<Vec3i>
    ): Boolean {
        // 检查控制器位置是否有效（一个结构只能有一个控制器）[citation:7]
        val controllerPositions = structurePattern
            .filter { controller.isControllerPosition(it) }
            .map { pos.offset(it) }

        if (controllerPositions.size != 1) {
            return false
        }

        // 检查是否与现有结构冲突
        if (hasExistingMultiblockConflict(level, pos, structurePattern)) {
            return false
        }

        return controller.validateSpecificRules(level, pos, structurePattern)
    }


    /**
     * 检查是否存在与现有多方块结构的冲突
     *
     * @param level 当前游戏世界等级对象
     * @param centerPos 多方块结构的中心位置
     * @param structurePattern 多方块结构的相对位置模式列表
     * @return 如果存在冲突返回true，否则返回false
     */
    private fun hasExistingMultiblockConflict(
        level: Level,
        centerPos: BlockPos,
        structurePattern: List<Vec3i>
    ): Boolean {
        // 遍历结构模式中的每个相对位置，检查是否与现有结构冲突
        for (relativePos in structurePattern) {
            val worldPos = centerPos.offset(relativePos)
            // 这里可以添加更复杂的冲突检测逻辑
            // 例如检查该位置是否已经是其他多方块结构的一部分
//            if (isPartOfOtherMultiblock(level, worldPos)) {
//                return true
//            }
        }
        return false
    }


    /**
     * 获取世界边界坐标
     *
     * @param level 游戏世界等级对象，用于获取构建高度限制
     * @return 包含两个BlockPos的Pair对象，第一个元素是最小边界坐标，第二个元素是最大边界坐标
     */
    private fun getWorldBounds(level: Level): Pair<BlockPos, BlockPos> {
        // 构造世界边界坐标对，使用最小和最大构建高度作为边界值
        return Pair(
            BlockPos(level.minBuildHeight, level.minBuildHeight, level.minBuildHeight),
            BlockPos(level.maxBuildHeight, level.maxBuildHeight, level.maxBuildHeight)
        )
    }


    /**
     * 扩展函数：检查位置是否在世界边界内
     *
     * @param pos 要检查的方块位置
     * @return 如果位置在世界建筑高度范围内则返回true，否则返回false
     */
    private fun Level.isInWorldBounds(pos: BlockPos): Boolean {
        // 检查Y坐标是否在有效建筑高度范围内
        return pos.y >= minBuildHeight && pos.y < maxBuildHeight
    }


    /**
     * 生成一个立方体区域内的所有方块位置
     * @param size 立方体的边长（从中心向每个方向延伸的距离）
     * @param center 立方体的中心位置
     * @return 包含所有方块位置的Set（避免重复位置）
     */
    fun generateCubeBlockPositions(size: Int, center: BlockPos): Set<BlockPos> {
        require(size > 0) { "Size must be positive" }

        val positions = mutableSetOf<BlockPos>()
        val halfSize = size / 2

        // 计算立方体的边界
        val minX = center.x - halfSize
        val maxX = center.x + halfSize
        val minY = center.y - halfSize
        val maxY = center.y + halfSize
        val minZ = center.z - halfSize
        val maxZ = center.z + halfSize

        // 遍历立方体内的所有位置
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    positions.add(BlockPos(x, y, z))
                }
            }
        }

        return positions
    }

    /**
     * 生成一个立方体区域内的所有方块位置（使用序列，内存效率更高）
     * @param size 立方体的边长
     * @param center 立方体的中心位置
     * @return 包含所有方块位置的List
     */
    fun generateCubeBlockPositionsEfficient(size: Int, center: BlockPos): List<BlockPos> {
        require(size > 0) { "Size must be positive" }

        val halfSize = size / 2

        return sequence {
            for (x in (center.x - halfSize)..(center.x + halfSize)) {
                for (y in (center.y - halfSize)..(center.y + halfSize)) {
                    for (z in (center.z - halfSize)..(center.z + halfSize)) {
                        yield(BlockPos(x, y, z))
                    }
                }
            }
        }.toList()
    }

    /**
     * 生成一个立方体区域内的所有方块位置（带边界检查）
     * @param size 立方体的边长
     * @param center 立方体的中心位置
     * @param worldMin 世界最小边界（可选）
     * @param worldMax 世界最大边界（可选）
     * @return 包含所有方块位置的Set
     */
    fun generateCubeBlockPositionsBounded(
        size: Int,
        center: BlockPos,
        worldMin: BlockPos? = null,
        worldMax: BlockPos? = null
    ): Set<BlockPos> {
        require(size > 0) { "Size must be positive" }

        val positions = mutableSetOf<BlockPos>()
        val halfSize = size / 2

        // 计算理论边界
        val minX = center.x - halfSize
        val maxX = center.x + halfSize
        val minY = center.y - halfSize
        val maxY = center.y + halfSize
        val minZ = center.z - halfSize
        val maxZ = center.z + halfSize

        // 应用世界边界限制
        val actualMinX = worldMin?.x?.coerceAtLeast(minX) ?: minX
        val actualMaxX = worldMax?.x?.coerceAtMost(maxX) ?: maxX
        val actualMinY = worldMin?.y?.coerceAtLeast(minY) ?: minY
        val actualMaxY = worldMax?.y?.coerceAtMost(maxY) ?: maxY
        val actualMinZ = worldMin?.z?.coerceAtLeast(minZ) ?: minZ
        val actualMaxZ = worldMax?.z?.coerceAtMost(maxZ) ?: maxZ

        // 遍历限制后的区域
        for (x in actualMinX..actualMaxX) {
            for (y in actualMinY..actualMaxY) {
                for (z in actualMinZ..actualMaxZ) {
                    positions.add(BlockPos(x, y, z))
                }
            }
        }

        return positions
    }

    /**
     * 生成空心立方体（只有外壳）的方块位置
     * @param size 立方体的边长
     * @param center 立方体的中心位置
     * @return 包含外壳方块位置的Set
     */
    fun generateHollowCubeBlockPositions(size: Int, center: BlockPos): Set<BlockPos> {
        require(size > 0) { "Size must be positive" }

        val positions = mutableSetOf<BlockPos>()
        val halfSize = size / 2

        val minX = center.x - halfSize
        val maxX = center.x + halfSize
        val minY = center.y - halfSize
        val maxY = center.y + halfSize
        val minZ = center.z - halfSize
        val maxZ = center.z + halfSize

        // 只添加表面的方块
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                positions.add(BlockPos(x, y, minZ)) // 底面
                positions.add(BlockPos(x, y, maxZ)) // 顶面
            }
        }

        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                positions.add(BlockPos(x, minY, z)) // 前面
                positions.add(BlockPos(x, maxY, z)) // 后面
            }
        }

        for (y in minY..maxY) {
            for (z in minZ..maxZ) {
                positions.add(BlockPos(minX, y, z)) // 左面
                positions.add(BlockPos(maxX, y, z)) // 右面
            }
        }

        return positions
    }

    companion object {
        fun findController(level: Level, pos: BlockPos) {}
        fun attemptFormStructure(level: Level, pos: BlockPos, controller: MultiblockController) {}
        fun breakStructure(level: Level, pos: BlockPos, controller: MultiblockController) {}
    }
}


// 扩展函数，直接在BlockPos上使用
//fun BlockPos.generateCubeAround(size: Int): Set<BlockPos> {
//    return MultiBlockGenerator().generateCubeBlockPositions(size, this)
//}