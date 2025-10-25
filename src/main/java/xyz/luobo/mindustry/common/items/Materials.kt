package xyz.luobo.mindustry.common.items

enum class Materials(
    val id: String
) {
    COPPER("copper"),
    LEAD("lead"),
    PLASTANIUM("plastanium"),
    PHASE_FABRIC("phase-fabric"),
    SURGE_ALLOY("surge-alloy"),
    SPORE_POD("spore-pod"),
    BLAST_COMPOUND("blast-compound"),
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