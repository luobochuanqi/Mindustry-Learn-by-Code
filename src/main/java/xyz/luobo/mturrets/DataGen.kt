package xyz.luobo.mturrets

import net.minecraft.core.HolderLookup
import net.minecraft.data.CachedOutput
import net.minecraft.data.DataGenerator
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import net.minecraft.data.loot.BlockLootSubProvider
import net.minecraft.data.loot.LootTableProvider
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.RecipeProvider
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.neoforged.neoforge.client.model.generators.BlockStateProvider
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder
import net.neoforged.neoforge.client.model.generators.ItemModelProvider
import net.neoforged.neoforge.client.model.generators.ModelFile
import net.neoforged.neoforge.common.data.ExistingFileHelper
import net.neoforged.neoforge.common.data.LanguageProvider
import net.neoforged.neoforge.data.event.GatherDataEvent
import net.minecraft.tags.BlockTags
import net.neoforged.neoforge.common.data.BlockTagsProvider
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import xyz.luobo.mturrets.common.ModSounds
import xyz.luobo.mturrets.common.worldgen.OreWorldGenProvider
import xyz.luobo.mturrets.common.ModBlocks
import xyz.luobo.mturrets.common.ModItems
import xyz.luobo.mturrets.common.items.Materials
import xyz.luobo.mturrets.common.liquids.Liquids
import xyz.luobo.mturrets.core.recipe.MachineRecipe
import xyz.luobo.mturrets.core.structure.StructuralBlock
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
object DataGen {
    fun generate(event: GatherDataEvent) {
        val generator: DataGenerator = event.generator
        val packOutput = generator.packOutput
        val existingFileHelper = event.existingFileHelper

        generator.addProvider(event.includeClient(), ModLanguageProvider(packOutput, "en_us"))
        generator.addProvider(event.includeClient(), ModSoundProvider(packOutput))
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
 * 声音事件 → sounds.json(#57):3 个 file 型条目,逐一对应 assets/mturrets/sounds/ 下的 ogg。
 * 事件注册在 [ModSounds](registry 数据);sounds.json 是客户端把事件关联到音源文件的桥,
 * 由本 provider 生成,CI 以 git diff 把关。type=file 的 name 是完整 resource location。
 */
class ModSoundProvider(private val output: PackOutput) : DataProvider {
    override fun run(cache: CachedOutput): CompletableFuture<*> {
        val json = JsonObject()
        listOf(
            ModSounds.SHOOT_DUO,
            ModSounds.SHOOT_SCATTER,
            ModSounds.MACHINE_HUM
        ).forEach { holder ->
            val entry = JsonObject()
            val sounds = JsonArray()
            val file = JsonObject()
            file.addProperty("type", "file")
            file.addProperty("name", holder.id.toString())
            sounds.add(file)
            entry.add("sounds", sounds)
            entry.addProperty("subtitle", "mturrets.subtitle.${holder.id.path}")
            json.add(holder.id.path, entry)
        }
        val path: Path = output.getOutputFolder().resolve("assets/${MTurrets.MOD_ID}/sounds.json")
        return DataProvider.saveStable(cache, json, path)
    }

    override fun getName(): String = "MTurrets Sounds"
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
        this.add(ModBlocks.POWER_SOURCE.get(), "Power Source")
        this.add(ModBlocks.COMBUSTION_GENERATOR.get(), "Combustion Generator")
        this.add(ModBlocks.DUO_BLOCK.get(), "Duo")
        this.add(ModBlocks.SCATTER.get(), "Scatter")
        this.add(ModBlocks.SCATTER_STRUCTURAL.get(), "Scatter Member")
        this.add(ModBlocks.ORE_COPPER.get(), "Copper Ore")
        this.add(ModBlocks.ORE_LEAD.get(), "Lead Ore")
        this.add(ModBlocks.ORE_COAL.get(), "Coal Ore")
        this.add(ModBlocks.DRILL.get(), "Mechanical Drill")
        this.add(ModBlocks.DRILL_STRUCTURAL.get(), "Mechanical Drill Member")
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

        // Jade tooltips(与 zh_cn.json 手工维护的 key 一一对应)
        this.add("jade.mturrets.ammo", "Ammo: %s/%s")
        this.add("jade.mturrets.progress", "Progress: %s%%")
        this.add("jade.mturrets.health", "Health: %s/%s")
        this.add("jade.mturrets.progress_supply", "Progress: %s%% (supply %s%%)")
        this.add("jade.mturrets.energy", "Energy: %s/%s FE")
        this.add("jade.mturrets.production", "Production: %s FE/t")
        this.add("jade.mturrets.fuel", "Burning: %s t / Fuel: %s")
        this.add("jade.mturrets.drill_lock", "Lock: %s")
        this.add("jade.mturrets.drill_reserve_typed", "%s: %s")
        this.add("jade.mturrets.drill_buffer", "Buffer: %s")
        this.add("jade.mturrets.drill_lock_none", "None")

        // 声音字幕(#57):sounds.json 的 subtitle 字段指向这些 key,声音字幕屏据此显示
        this.add("mturrets.subtitle.shoot_duo", "Duo Turret")
        this.add("mturrets.subtitle.shoot_scatter", "Scatter Turret")
        this.add("mturrets.subtitle.machine_hum", "Machine running")

        // 炮台 LOS 视线 debug 可视化(#77):客户端命令开关反馈
        this.add("mturrets.command.debug.on", "Turret LOS debug rendering enabled")
        this.add("mturrets.command.debug.off", "Turret LOS debug rendering disabled")

        // Jade plugin config entries(缺失会导致客户端断言崩溃)
        this.add("config.jade.plugin_mturrets.structure_data", "Structure Info")
    }
}

class ModItemModelProvider(output: PackOutput, existingFileHelper: ExistingFileHelper) :
    ItemModelProvider(output, MTurrets.MOD_ID, existingFileHelper) {
    override fun registerModels() {
        // 方块物品:默认引用同名方块模型。必须带 mturrets 命名空间——无命名空间的 "block/x"
        // 会被解析成 minecraft:block/x(缺失 → 物品/Jade 图标渲染成空骨架,#47)
        // 成员格无物品(#59 定案:拾取栈代理回锚点),不生成死 item 模型
        ModBlocks.MOD_BLOCKS.entries
            .filterNot { it.get() is StructuralBlock }
            .forEach { entry ->
                val name = entry.id.path
                getBuilder(name).parent(ModelFile.UncheckedModelFile(modLoc("block/$name").toString()))
            }

        // 全模型资产架构(#42):炮台物品引用 full 模型,整座(含动件)进 GUI;
        // display 纯公式:vanilla block 展示值 × s,s = 1/占地边长(模型按占地等比缩放回一格,
        // 几何以原点格为角,缩放后自动居中)
        mapOf(
            "duo" to ("block/turret/duo_full" to 1),
            "scatter" to ("block/turret/scatter_full" to 2)
        ).forEach { (name, spec) ->
            val (model, footprint) = spec
            applyBlockDisplay(
                getBuilder(name).parent(ModelFile.UncheckedModelFile(modLoc(model).toString())),
                1f / footprint
            )
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

    /**
     * 炮台物品的 display 变换(#42 纯公式):vanilla 方块展示值,scale 乘等比因子 s。
     * full 模型几何以原点格为角(占地 span = footprint×16),绕原点缩放 s 后
     * 自然落回 0..16 一格体量,故 translation 沿用 vanilla 值、不加手调偏移。
     */
    private fun applyBlockDisplay(builder: ItemModelBuilder, s: Float) {
        val t = builder.transforms()
        t.transform(ItemDisplayContext.GUI)
            .rotation(30f, 225f, 0f).scale(0.625f * s).end()
        t.transform(ItemDisplayContext.GROUND)
            .translation(0f, 3f, 0f).scale(0.25f * s).end()
        t.transform(ItemDisplayContext.FIXED)
            .rotation(-90f, 0f, 0f).translation(0f, 0f, -4f).scale(0.5f * s).end()
        t.transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
            .rotation(75f, 45f, 0f).translation(0f, 2.5f, 0f).scale(0.375f * s).end()
        t.transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
            .rotation(75f, 45f, 0f).translation(0f, 2.5f, 0f).scale(0.375f * s).end()
        t.transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
            .rotation(0f, 45f, 0f).scale(0.4f * s).end()
        t.transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
            .rotation(0f, 225f, 0f).scale(0.4f * s).end()
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
        // 电源(#49,调试):暂沿用节点贴图占位,新模型随"全模型资产架构"票落定
        this.simpleBlockWithItem(
            ModBlocks.POWER_SOURCE.get(),
            this.models().cubeAll("power_source", this.modLoc("block/power_node_block"))
        )
        // 燃烧发电机(#56):暂沿用节点贴图占位,新模型随"全模型资产架构"票落定(同电源)
        this.simpleBlockWithItem(
            ModBlocks.COMBUSTION_GENERATOR.get(),
            this.models().cubeAll("combustion_generator", this.modLoc("block/power_node_block"))
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
        this.simpleBlock(
            ModBlocks.DRILL_STRUCTURAL.get(),
            this.models().cubeBottomTop(
                "mechanical_drill_structural",
                this.modLoc("block/mechanical_drill"),
                this.modLoc("block/mechanical_drill_top"),
                this.modLoc("block/mechanical_drill_top")
            )
        )

        // 全模型资产架构(#42):炮台锚点与成员格方块侧渲染为空(几何由锚点 BE visual 承担),
        // blockstate 只保留 elementless 的 particle 模型,供敲击/破坏粒子取色
        val duoEmpty = this.models().getBuilder("block/turret/duo_empty")
            .texture("particle", this.modLoc("block/turret/duo_parts"))
        val scatterEmpty = this.models().getBuilder("block/turret/scatter_empty")
            .texture("particle", this.modLoc("block/turret/scatter_parts"))
        this.simpleBlock(ModBlocks.DUO_BLOCK.get(), duoEmpty)
        this.simpleBlock(ModBlocks.SCATTER.get(), scatterEmpty)
        for (x in 0..2) for (y in 0..2) for (z in 0..2) {
            // 27 个偏移编码变体仍需覆盖(INVISIBLE 不免 blockstate 查表),几何为空无 yaw
            this.getVariantBuilder(ModBlocks.SCATTER_STRUCTURAL.get())
                .partialState()
                .with(StructuralBlock.OFFSET_X, x)
                .with(StructuralBlock.OFFSET_Y, y)
                .with(StructuralBlock.OFFSET_Z, z)
                .modelForState().modelFile(scatterEmpty).addModel()
        }

        // 蓝图管线骨架临时方块(贴图复用现有素材,真内容落地后删除)
        val testTexture = this.modLoc("block/power_node_block")
        this.simpleBlockWithItem(
            ModBlocks.TEST_STRUCTURE_ANCHOR_2X2.get(),
            this.models().cubeAll("test_structure_anchor_2x2", testTexture)
        )
        this.simpleBlock(
            ModBlocks.TEST_STRUCTURAL.get(),
            this.models().cubeAll("test_structure_structural", testTexture)
        )
    }
}
/**
 * 掉落表:锚点 dropSelf(控制器物品);成员 noDrop(掉落收口在锚点,ADR-0003)。
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
        this.dropSelf(ModBlocks.POWER_SOURCE.get())
        this.dropSelf(ModBlocks.COMBUSTION_GENERATOR.get())
        this.dropSelf(ModBlocks.TEST_STRUCTURE_ANCHOR_2X2.get())
        // 成员格:properties noLootTable() 已豁免(掉落收口在锚点,ADR-0003)
        // 新 Duo(#31):蓝图锚点 dropSelf(控制器物品),拆机内容物折回由锚点散落通道兜住
        this.dropSelf(ModBlocks.DUO_BLOCK.get())
        // Scatter(#34):同 Duo;成员格 noLootTable(掉落收口在锚点)
        this.dropSelf(ModBlocks.SCATTER.get())
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
