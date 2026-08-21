package xyz.luobo.mturrets

import net.minecraft.data.DataGenerator
import net.minecraft.data.PackOutput
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


object DataGen {
    fun generate(event: GatherDataEvent) {
        val generator: DataGenerator = event.generator
        val packOutput = generator.packOutput
        val existingFileHelper = event.existingFileHelper

        generator.addProvider(event.includeClient(), ModLanguageProvider(packOutput, "en_us"))
        generator.addProvider(event.includeClient(), ModItemModelProvider(packOutput, existingFileHelper))
        generator.addProvider(event.includeClient(), ModBlockStateProvider(packOutput, existingFileHelper))
    }
}

class ModLanguageProvider(output: PackOutput, locale: String) : LanguageProvider(output, MTurrets.MOD_ID, locale) {
    override fun addTranslations() {
        // Item Group
        this.add("itemGroup.mturrets", "MTurrets")

        // Blocks
        this.add(ModBlocks.POWER_NODE_BLOCK.get(), "Power Node")
        this.add(ModBlocks.KILN_BLOCK.get(), "Kiln")
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
        this.simpleBlockWithItem(ModBlocks.KILN_BLOCK.get(), cubeAll(ModBlocks.KILN_BLOCK.get()))

        // 静态模型炮台(贴图提取自 Mindustry 开源仓库,出处见 README)
        val arc = ModBlocks.ARC_BLOCK.get()
        this.simpleBlockWithItem(arc, this.cubeAll(arc))
        val meltdown = ModBlocks.MELTDOWN_BLOCK.get()
        this.simpleBlockWithItem(meltdown, this.cubeAll(meltdown))
    }
}
