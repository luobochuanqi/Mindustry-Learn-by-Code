import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.world.level.Level

interface IMultiblockController {
    /**
     * 获取多方块结构的相对位置模式[citation:7]
     * @param size 结构尺寸
     * @return 相对于中心位置的方块偏移列表
     */
    fun getStructurePattern(size: Int): List<Vec3i>

    /**
     * 检查指定位置是否适合作为控制器位置[citation:7]
     * @param relativePos 相对位置
     * @return 是否为控制器位置
     */
    fun isControllerPosition(relativePos: Vec3i): Boolean

    /**
     * 检查指定位置的方块是否有效[citation:7]
     * @param level 世界
     * @param worldPos 世界坐标
     * @param relativePos 在结构中的相对位置
     * @return 该位置是否有效
     */
    fun isValidBlockForPosition(level: Level, worldPos: BlockPos, relativePos: Vec3i): Boolean

    /**
     * 验证特定于该控制器的规则[citation:7]
     */
    fun validateSpecificRules(level: Level, centerPos: BlockPos, structurePattern: List<Vec3i>): Boolean
}