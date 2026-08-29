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
            ModRecipeProvider(packOutput, event.lookupProvider)
        )
    }
}

class ModLanguageProvider(output: PackOutput, locale: String) : LanguageProvider(output, MTurrets.MOD_ID, locale) {
    override fun addTranslations() {
        // Item Group
        this.add("itemGroup.mturrets", "MTurrets")

        // Blocks
        this.add(ModBlocks.POWER_NODE_BLOCK.get(), "Power Node")
        this.add(ModBlocks.KILN.get(), "Kiln")
        this.add(ModBlocks.DUO_BLOCK.get(), "Duo")
        this.add(ModBlocks.ARC_BLOCK.get(), "Arc")
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

        // Configs
        this.add("mturrets.configuration.maxRenderDistance", "Max Laser Render Distance")
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
        this.add("config.jade.plugin_mturrets.power_node_data", "Power Node Info")
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
        this.simpleBlockWithItem(ModBlocks.POWER_NODE_BLOCK.get(), cubeAll(ModBlocks.POWER_NODE_BLOCK.get()))
        // 窑炉:贴图沿用 kiln_block(与 legacy 视觉一致,#33 决议)
        this.simpleBlockWithItem(ModBlocks.KILN.get(), this.models().cubeAll("kiln", this.modLoc("block/kiln_block")))

        // 静态模型炮台(贴图提取自 Mindustry 开源仓库,出处见 textures/ATTRIBUTION.md)
        val arc = ModBlocks.ARC_BLOCK.get()
        this.simpleBlockWithItem(arc, this.cubeAll(arc))
        val meltdown = ModBlocks.MELTDOWN_BLOCK.get()
        this.simpleBlockWithItem(meltdown, this.cubeAll(meltdown))
        val duo = ModBlocks.DUO_BLOCK.get()
        this.simpleBlockWithItem(duo, this.cubeAll(duo))

        // 蓝图管线骨架临时方块(贴图复用现有素材,真内容落地后删除)
        val testTexture = this.blockTexture(ModBlocks.POWER_NODE_BLOCK.get())
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
        this.dropSelf(ModBlocks.TEST_STRUCTURE_ANCHOR_2X2.get())
        // 成员格:properties noLootTable() 已豁免(掉落收口在锚点,ADR-0003)
        // LEGACY 方块:维持现状零掉落
        this.add(ModBlocks.POWER_NODE_BLOCK.get(), noDrop())
        this.add(ModBlocks.DUO_BLOCK.get(), noDrop())
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
