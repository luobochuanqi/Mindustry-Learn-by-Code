package xyz.luobo.mindustry.common.liquids

enum class Liquids(
    val id: String
) {
    WATER("water"),
    SLAG("slag"),
    CYANOGEN("cyanogen"),
    OIL("oil"),
    CRYOFLUID("cryofluid"),
    NEOPLASM("neoplasm"),
    ARKYCITE("arkycite"),
    OZONE("ozone"),
    HYDROGEN("hydrogen"),
    NITROGEN("nitrogen"),
    GALLIUM("gallium");

    companion object {
        // 用于 DataGen 遍历
        val ALL = entries.toList()
    }

    val displayName: String
        get() = id.split("_").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}