package xyz.luobo.mindustry.common.items

enum class Materials(
    val id: String
) {
    COPPER("copper"),
    LEAD("lead"),
    PLASTANIUM("plastanium"),
    PHASE_FABRIC("phase_fabric"),
    SURGE_ALLOY("surge_alloy"),
    SPORE_POD("spore_pod"),
    BLAST_COMPOUND("blast_compound"),
    PYRATITE("pyratite"),
    BERYLLIUM("beryllium"),
    TUNGSTEN("tungsten"),
    OXIDE("oxide"),
    CARBIDE("carbide"),
    METAGLASS("metaglass"),
    GRAPHITE("graphite"),
    SAND("sand"),
    COAL("coal"),
    TITANIUM("titanium"),
    THORIUM("thorium"),
    SCRAP("scrap"),
    SILICON("silicon");

    companion object {
        // 用于 DataGen 遍历
        val ALL = entries.toList()
    }
}