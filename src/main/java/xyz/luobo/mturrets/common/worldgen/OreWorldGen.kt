package xyz.luobo.mturrets.common.worldgen

import net.minecraft.core.HolderSet
import net.minecraft.core.RegistrySetBuilder
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.BiomeTags
import net.minecraft.tags.BlockTags
import net.minecraft.util.valueproviders.UniformInt
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.levelgen.GenerationStep
import net.minecraft.world.level.levelgen.VerticalAnchor
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration
import net.minecraft.world.level.levelgen.heightproviders.TrapezoidHeight
import net.minecraft.world.level.levelgen.placement.BiomeFilter
import net.minecraft.world.level.levelgen.placement.CountPlacement
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement
import net.minecraft.world.level.levelgen.placement.InSquarePlacement
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.levelgen.placement.PlacedFeature
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest
import net.neoforged.neoforge.common.world.BiomeModifiers.AddFeaturesBiomeModifier
import net.neoforged.neoforge.common.world.BiomeModifier
import net.neoforged.neoforge.registries.NeoForgeRegistries
import net.neoforged.neoforge.registries.DeferredBlock
import net.minecraft.data.PackOutput
import net.minecraft.core.HolderLookup
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider
import java.util.concurrent.CompletableFuture
import xyz.luobo.mturrets.MTurrets
import xyz.luobo.mturrets.common.ModBlocks

/**
 * 一期矿脉世界生成(#35):每矿 = ConfiguredFeature(`minecraft:ore` blob)→ PlacedFeature
 * (count 1–2 → in_square → trapezoid 高度 → biome)→ BiomeModifier(`neoforge:add_features`,
 * step underground_ores,挂 #minecraft:is_overworld);全走 datagen RegistrySetBuilder +
 * DatapackBuiltinEntriesProvider(#29 事实 §2 固定组合)。每矿一个 placed feature 只挂一个
 * modifier(#29 陷阱①:feature cycle 违例)。高度提供器用 TrapezoidHeight(1.21.1 的
 * HeightRangePlacement 只有 uniform/triangle 快捷方法,#29 事实 §4.2)。
 *
 * 参数为 #24 定案起点值,本文件即代码表:size≈48、count 1–2、discard≈0.7、窄高度带。
 */
object OreWorldGen {

    /** 每矿参数表:形状/数量/高度带,重平衡改这里(#29 事实 §4.4 方向)。 */
    private data class OreSpec(
        val block: DeferredBlock<Block>,
        val configured: ResourceKey<ConfiguredFeature<*, *>>,
        val placed: ResourceKey<PlacedFeature>,
        val modifier: ResourceKey<BiomeModifier>,
        val size: Int,
        val discardOnAirExposure: Float,
        val countMin: Int,
        val countMax: Int,
        val minY: Int,
        val maxY: Int
    )

    private val ORES = listOf(
        // 铜/铅:浅层 −16…96;煤:0…192(#24/ #25 定案)
        ore("ore_copper", ModBlocks.ORE_COPPER, -16, 96),
        ore("ore_lead", ModBlocks.ORE_LEAD, -16, 96),
        ore("ore_coal", ModBlocks.ORE_COAL, 0, 192)
    )

    private fun ore(
        id: String,
        block: DeferredBlock<Block>,
        minY: Int,
        maxY: Int
    ) = OreSpec(
        block = block,
        configured = ResourceKey.create(Registries.CONFIGURED_FEATURE, modLoc(id)),
        placed = ResourceKey.create(Registries.PLACED_FEATURE, modLoc(id)),
        modifier = ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, modLoc(id)),
        size = 48,
        discardOnAirExposure = 0.7f,
        countMin = 1,
        countMax = 2,
        minY = minY,
        maxY = maxY
    )

    private fun modLoc(path: String): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(MTurrets.MOD_ID, path)

    /** datagen 用 RegistrySetBuilder:configured + placed + biome modifier 一次产出(#29 §2)。 */
    fun builder(): RegistrySetBuilder = RegistrySetBuilder()
        .add(Registries.CONFIGURED_FEATURE) { ctx ->
            for (ore in ORES) {
                ctx.register(
                    ore.configured,
                    ConfiguredFeature(
                        Feature.ORE,
                        OreConfiguration(
                            listOf(
                                // 同一矿石同时替换 stone 与 deepslate 宿主(#24:单变体,不做双色)
                                OreConfiguration.target(
                                    TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES),
                                    ore.block.get().defaultBlockState()
                                ),
                                OreConfiguration.target(
                                    TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES),
                                    ore.block.get().defaultBlockState()
                                )
                            ),
                            ore.size,
                            ore.discardOnAirExposure
                        )
                    )
                )
            }
        }
        .add(Registries.PLACED_FEATURE) { ctx ->
            val configured = ctx.lookup(Registries.CONFIGURED_FEATURE)
            for (ore in ORES) {
                ctx.register(
                    ore.placed,
                    PlacedFeature(
                        configured.getOrThrow(ore.configured),
                        listOf(
                            CountPlacement.of(UniformInt.of(ore.countMin, ore.countMax)),
                            InSquarePlacement.spread(),
                            HeightRangePlacement.of(
                                TrapezoidHeight.of(
                                    VerticalAnchor.absolute(ore.minY),
                                    VerticalAnchor.absolute(ore.maxY)
                                )
                            ),
                            BiomeFilter.biome()
                        )
                    )
                )
            }
        }
        .add(NeoForgeRegistries.Keys.BIOME_MODIFIERS) { ctx ->
            val biomes = ctx.lookup(Registries.BIOME)
            val placed = ctx.lookup(Registries.PLACED_FEATURE)
            for (ore in ORES) {
                ctx.register(
                    ore.modifier,
                    AddFeaturesBiomeModifier(
                        biomes.getOrThrow(BiomeTags.IS_OVERWORLD),
                        HolderSet.direct(placed.getOrThrow(ore.placed)),
                        GenerationStep.Decoration.UNDERGROUND_ORES
                    )
                )
            }
        }
}

/** 一期矿脉世界生成 datagen 入口(#29 事实 §2:RegistrySetBuilder + DatapackBuiltinEntriesProvider)。 */
class OreWorldGenProvider(
    output: PackOutput,
    registries: CompletableFuture<HolderLookup.Provider>
) : DatapackBuiltinEntriesProvider(output, registries, OreWorldGen.builder(), setOf(MTurrets.MOD_ID))