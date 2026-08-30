package xyz.luobo.mturrets

import net.minecraft.core.HolderLookup
import net.minecraft.data.DataGenerator
import net.minecraft.data.PackOutput
import net.minecraft.data.loot.BlockLootSubProvider
import net.minecraft.data.loot.LootTableProvider
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.RecipeProvider
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.neoforged.neoforge.client.model.generators.BlockStateProvider
import net.neoforged.neoforge.client.model.generators.ItemModelProvider
import net.neoforged.neoforge.client.model.generators.ModelFile
import net.neoforged.neoforge.common.data.ExistingFileHelper
import net.neoforged.neoforge.common.data.LanguageProvider
import net.neoforged.neoforge.data.event.GatherDataEvent
import net.minecraft.tags.BlockTags
import net.neoforged.neoforge.common.data.BlockTagsProvider
import xyz.luobo.mturrets.common.worldgen.OreWorldGenProvider
import xyz.luobo.mturrets.common.ModBlocks
import xyz.luobo.mturrets.common.ModItems
import xyz.luobo.mturrets.common.items.Materials
import xyz.luobo.mturrets.common.liquids.Liquids
import xyz.luobo.mturrets.core.recipe.MachineRecipe
import java.util.concurrent.CompletableFuture

object DataGen {
    fun generate(event: GatherDataEvent) {
        val generator: DataGenerator = event.generator
        val packOutput = generator.packOutput
        val existingFileHelper = event.existingFileHelper

        generator.addProvider(event.includeClient(), ModLanguageProvider(packOutput, "en_us"))
        generator.addProvider(event.includeClient(), ModItemModelProvider(packOutput, existingFileHelper))
        generator.addProvider(event.includeClient(), ModBlockStateProvider(packOutput, existingFileHelper))
        generator.addProvider(
            event.includeServer(),
            LootTableProvider(
                packOutput,
                emptySet(),
                listOf(
                    LootTableProvider.SubProviderEntry(
                        { registries -> ModBlockLootProvider(registries) },
                        LootContextParamSets.BLOCK
                    )
                ),
                event.lookupProvider
            )
        )
        generator.addProvider(
            event.includeServer(),
            OreWorldGenProvider(packOutput, event.lookupProvider)
        )
        generator.addProvider(
            event.includeServer(),
            ModBlockTagProvider(packOutput, event.lookupProvider, existingFileHelper)
        )
        generator.addProvider(
            event.includeServer(),
            ModRecipeProvider(packOutput, event.lookupProvider)
        )
    }
}

/**
 * 方块标签(#35):矿石挂 #minecraft:mineable/pickaxe;不做 requiresCorrectToolForDrops
 * (#24 手挖亦可),不需其他标签。
 */
class ModBlockTagProvider(
    output: PackOutput,
    registries: CompletableFuture<HolderLookup.Provider>,
    existingFileHelper: ExistingFileHelper
) : BlockTagsProvider(output, registries, MTurrets.MOD_ID, existingFileHelper) {
    override fun addTags(provider: HolderLookup.Provider) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
            ModBlocks.ORE_COPPER.get(),
            ModBlocks.ORE_LEAD.get(),
            ModBlocks.ORE_COAL.get()
        )
    }
}

class ModLanguageProvider(output: PackOutput, locale: String) : LanguageProvider(output, MTurrets.MOD_ID, locale) {
    override fun addTranslations() {
        // Item Group
        this.add("itemGroup.mturrets", "MTurrets")

        // Blocks
        this.add(ModBlocks.POWER_NODE.get(), "Power Node")
        this.add(ModBlocks.BATTERY.get(), "Battery")
        this.add(ModBlocks.KILN.get(), "Kiln")
        this.add(ModBlocks.DUO_BLOCK.get(), "Duo")
        this.add(ModBlocks.ARC_BLOCK.get(), "Arc")
        this.add(ModBlocks.ORE_COPPER.get(), "Copper Ore")
        this.add(ModBlocks.ORE_LEAD.get(), "Lead Ore")
        this.add(ModBlocks.ORE_COAL.get(), "Coal Ore")
        this.add(ModBlocks.DRILL.get(), "Mechanical Drill")
        this.add(ModBlocks.DRILL_STRUCTURAL.get(), "Mechanical Drill Member")
        this.add(ModBlocks.MELTDOWN_BLOCK.get(), "Meltdown")
        Materials.ALL.forEach { material ->
            this.add(ModItems.getMaterial(material).get(), material.displayName)
        }

        // Liquids (物品)
        Liquids.ALL.forEach { liquid ->
            this.add(ModItems.getLiquid(liquid).get(), liquid.displayName)
        }

        // Fluids (流体) - 自动生成
        Liquids.ALL.forEach { liquid ->
            this.add("fluid.mturrets.${liquid.id}", liquid.displayName)
        }

        this.add(ModBlocks.TEST_STRUCTURE_ANCHOR_2X2.get(), "Test Structure Anchor (2x2)")
        this.add(ModBlocks.TEST_STRUCTURAL.get(), "Test Structure Member")

        // 蓝图管线
        this.add("mturrets.message.blueprint_blocked", "Not enough space for structure")

        // Jade tooltips
        this.add("jade.mturrets.ammo", "Ammo: %s/%s")
        this.add("jade.mturrets.energy", "Energy: %s/%s FE")
        this.add("jade.mturrets.progress", "Progress: %s%%")

        // Jade plugin config entries(缺失会导致客户端断言崩溃)
        this.add("config.jade.plugin_mturrets.turret_data", "Turret Info")
    }
}

class ModItemModelProvider(output: PackOutput, existingFileHelper: ExistingFileHelper) :
    ItemModelProvider(output, MTurrets.MOD_ID, existingFileHelper) {
    override fun registerModels() {
        // 方块物品:直接引用 blockstate 提供器同轮生成的方块模型
        // (使用 Unchecked 父模型,保证单次 runData 即可全量产出,CI 新鲜度校验可幂等)
        ModBlocks.MOD_BLOCKS.entries.forEach { entry ->
            val name = entry.id.path
            getBuilder(name)
                .parent(ModelFile.UncheckedModelFile("block/$name"))
        }

        // Materials
        Materials.ALL.forEach { material ->
            this.basicItem(ModItems.getMaterial(material).get())
        }

        // Liquids
        Liquids.ALL.forEach { liquid ->
            getBuilder(liquid.id)
                .parent(ModelFile.UncheckedModelFile("item/generated"))
                .texture("layer0", modLoc("item/liquid/" + liquid.id))
        }
    }
}

class ModBlockStateProvider(output: PackOutput, existingFileHelper: ExistingFileHelper) :
    BlockStateProvider(output, MTurrets.MOD_ID, existingFileHelper) {
    override fun registerStatesAndModels() {
        // 电网(#30):节点沿用 #39 预置节点贴图;电池用预置 battery/battery_top
        this.simpleBlockWithItem(
            ModBlocks.POWER_NODE.get(),
            this.models().cubeAll("power_node", this.modLoc("block/power_node_block"))
        )
        this.simpleBlockWithItem(
            ModBlocks.BATTERY.get(),
            this.models().cubeBottomTop(
                "battery",
                this.modLoc("block/battery"),
                this.modLoc("block/battery_top"),
                this.modLoc("block/battery_top")
            )
        )
        // 窑炉:贴图沿用 kiln_block(与 legacy 视觉一致,#33 决议)
        this.simpleBlockWithItem(ModBlocks.KILN.get(), this.models().cubeAll("kiln", this.modLoc("block/kiln_block")))
        // 矿脉(#35):预置贴图(#39 入库),单变体 cube_all
        this.simpleBlockWithItem(ModBlocks.ORE_COPPER.get(), this.models().cubeAll("ore_copper", this.modLoc("block/ore_copper")))
        this.simpleBlockWithItem(ModBlocks.ORE_LEAD.get(), this.models().cubeAll("ore_lead", this.modLoc("block/ore_lead")))
        this.simpleBlockWithItem(ModBlocks.ORE_COAL.get(), this.models().cubeAll("ore_coal", this.modLoc("block/ore_coal")))
        // 钻头(#35):静态模型(ADR-0005 一期),锚点与成员格同外观;贴图预置 mechanical_drill*
        this.simpleBlockWithItem(
            ModBlocks.DRILL.get(),
            this.models().cubeBottomTop(
                "mechanical_drill",
                this.modLoc("block/mechanical_drill"),
                this.modLoc("block/mechanical_drill_top"),
                this.modLoc("block/mechanical_drill_top")
            )
        )
        this.simpleBlockWithItem(
            ModBlocks.DRILL_STRUCTURAL.get(),
            this.models().cubeBottomTop(
                "mechanical_drill_structural",
                this.modLoc("block/mechanical_drill"),
                this.modLoc("block/mechanical_drill_top"),
                this.modLoc("block/mechanical_drill_top")
            )
        )

        // 静态模型炮台(贴图提取自 Mindustry 开源仓库,出处见 textures/ATTRIBUTION.md)
        val arc = ModBlocks.ARC_BLOCK.get()
        this.simpleBlockWithItem(arc, this.cubeAll(arc))
        val meltdown = ModBlocks.MELTDOWN_BLOCK.get()
        this.simpleBlockWithItem(meltdown, this.cubeAll(meltdown))
        // Duo(#31):块状态模型 = 静态基座(与 Flywheel 部件同几何)。
        // 块状态模型走 chunk mesh 独立渲染路径,全立方会整体罩住内部部件——
        // 基座只占底部 1/4,旋转炮身/炮管由 Flywheel visual 在其上叠加,互不遮挡。
        val duo = ModBlocks.DUO_BLOCK.get()
        this.simpleBlockWithItem(duo, this.models().getExistingFile(this.modLoc("block/turret/duo_base")))

        // 蓝图管线骨架临时方块(贴图复用现有素材,真内容落地后删除)
        val testTexture = this.modLoc("block/power_node_block")
        this.simpleBlockWithItem(
            ModBlocks.TEST_STRUCTURE_ANCHOR_2X2.get(),
            this.models().cubeAll("test_structure_anchor_2x2", testTexture)
        )
        this.simpleBlockWithItem(
            ModBlocks.TEST_STRUCTURAL.get(),
            this.models().cubeAll("test_structure_structural", testTexture)
        )
    }
}
/**
 * 掉落表:锚点 dropSelf(控制器物品);成员 noDrop(掉落收口在锚点,ADR-0003)。
 * LEGACY 方块补 noDrop 表维持现状(零掉落),真内容落地时随各票改表。
 */
class ModBlockLootProvider(registries: HolderLookup.Provider) :
    BlockLootSubProvider(emptySet<Item>(), FeatureFlags.DEFAULT_FLAGS, registries) {
    override fun getKnownBlocks(): Iterable<Block> = ModBlocks.MOD_BLOCKS.entries.map { it.get() }

    override fun generate() {
        this.dropSelf(ModBlocks.KILN.get())
        // 矿脉(#35):固定 1 对应材料物品,无 fortune/silk touch 分支(#24 定案)
        this.add(ModBlocks.ORE_COPPER.get(), createSingleItemTable(ModItems.getMaterial(Materials.COPPER).get()))
        this.add(ModBlocks.ORE_LEAD.get(), createSingleItemTable(ModItems.getMaterial(Materials.LEAD).get()))
        this.add(ModBlocks.ORE_COAL.get(), createSingleItemTable(ModItems.getMaterial(Materials.COAL).get()))
        this.dropSelf(ModBlocks.DRILL.get())
        this.dropSelf(ModBlocks.POWER_NODE.get())
        this.dropSelf(ModBlocks.BATTERY.get())
        this.dropSelf(ModBlocks.TEST_STRUCTURE_ANCHOR_2X2.get())
        // 成员格:properties noLootTable() 已豁免(掉落收口在锚点,ADR-0003)
        // 新 Duo(#31):蓝图锚点 dropSelf(控制器物品),拆机内容物折回由锚点散落通道兜住
        this.dropSelf(ModBlocks.DUO_BLOCK.get())
        // LEGACY 方块:维持现状零掉落
        this.add(ModBlocks.ARC_BLOCK.get(), noDrop())
        this.add(ModBlocks.MELTDOWN_BLOCK.get(), noDrop())
    }
}

/**
 * 机器工艺配方默认值(#25 决议数值):代码表为源,datagen 落 JSON 后 datapack 可覆盖(ADR-0006)。
 * 一期唯一配方:窑炉 1 铅 + 1 原版沙 → 1 金属玻璃,100 tick,500 FE;水为机器语义不进 JSON。
 */
class ModRecipeProvider(output: PackOutput, registries: CompletableFuture<HolderLookup.Provider>) :
    RecipeProvider(output, registries) {
    override fun buildRecipes(out: RecipeOutput) {
        out.accept(
            ResourceLocation.fromNamespaceAndPath(MTurrets.MOD_ID, "kiln/metaglass"),
            MachineRecipe(
                ingredients = listOf(
                    Ingredient.of(ModItems.getMaterial(Materials.LEAD).get()),
                    Ingredient.of(net.minecraft.world.item.Items.SAND)
                ),
                results = listOf(ItemStack(ModItems.getMaterial(Materials.METAGLASS).get())),
                processingTime = 100,
                energy = 500
            ),
            null
        )
    }
}
