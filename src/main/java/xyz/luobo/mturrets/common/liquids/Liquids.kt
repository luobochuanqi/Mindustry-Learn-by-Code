package xyz.luobo.mturrets.common.liquids

/**
 * 液体类型枚举
 * 包含 MTurrets 中的所有液体及其颜色属性
 * 颜色值由 script/extract_colors.py 从纹理自动提取（众数颜色）
 */
enum class Liquids(
    val id: String,
    val color: Int  // 0xAARRGGBB 格式
) {
    ARKYCITE("arkycite", 0xFF80A848.toInt()),      // 青绿色液体
    CRYOFLUID("cryofluid", 0xFF80C8E8.toInt()),    // 浅蓝色冷冻液
    CYANOGEN("cyanogen", 0xFF88E8B8.toInt()),      // 青蓝色氰气
    GALLIUM("gallium", 0xFF9898B8.toInt()),        // 银灰色镓
    HYDROGEN("hydrogen", 0xFFD0E0F8.toInt()),      // 极浅蓝色氢气
    NEOPLASM("neoplasm", 0xFFE05038.toInt()),      // 橙红色原生质
    NITROGEN("nitrogen", 0xFFF8F8F8.toInt()),      // 近乎白色氮气
    OIL("oil", 0xFF303030.toInt()),                // 深灰色石油
    OZONE("ozone", 0xFFF8B8D0.toInt()),            // 粉紫色臭氧
    SLAG("slag", 0xFFF8C860.toInt()),              // 金黄色炉渣
    WATER("water", 0xFF4868C8.toInt());            // 蓝色水

    companion object {
        /**
         * 所有液体类型列表
         * 用于 DataGen 遍历
         */
        val ALL = entries.toList()

        /**
         * 通过ID查找液体
         */
        fun byId(id: String): Liquids? {
            return entries.find { it.id == id }
        }

        /**
         * 通过颜色查找最接近的液体
         */
        fun byColor(color: Int): Liquids? {
            return entries.minByOrNull { colorDistance(it.color, color) }
        }

        /**
         * 计算两种颜色的欧氏距离
         */
        private fun colorDistance(c1: Int, c2: Int): Double {
            val r1 = (c1 shr 16) and 0xFF
            val g1 = (c1 shr 8) and 0xFF
            val b1 = c1 and 0xFF
            val r2 = (c2 shr 16) and 0xFF
            val g2 = (c2 shr 8) and 0xFF
            val b2 = c2 and 0xFF
            return kotlin.math.sqrt(
                ((r1 - r2) * (r1 - r2) +
                        (g1 - g2) * (g1 - g2) +
                        (b1 - b2) * (b1 - b2)).toDouble()
            )
        }
    }

    /**
     * 显示名称（首字母大写）
     */
    val displayName: String
        get() = id.split("_").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    /**
     * 获取颜色分量
     */
    val red: Int get() = (color shr 16) and 0xFF
    val green: Int get() = (color shr 8) and 0xFF
    val blue: Int get() = color and 0xFF
    val alpha: Int get() = (color shr 24) and 0xFF

    /**
     * 转换为 Minecraft 的十进制颜色格式（用于药水等）
     */
    val decimalColor: Int
        get() = color and 0xFFFFFF

    /**
     * 是否为气态
     */
    val isGas: Boolean
        get() = this == HYDROGEN || this == NITROGEN || this == OZONE || this == CYANOGEN
}
