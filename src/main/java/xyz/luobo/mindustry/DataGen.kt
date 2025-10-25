package xyz.luobo.mindustry

import net.minecraft.data.DataGenerator
import net.minecraft.data.PackOutput
import net.neoforged.neoforge.client.model.generators.BlockStateProvider
import net.neoforged.neoforge.client.model.generators.ItemModelProvider
import net.neoforged.neoforge.common.data.ExistingFileHelper
import net.neoforged.neoforge.common.data.LanguageProvider
import net.neoforged.neoforge.data.event.GatherDataEvent
import xyz.luobo.mindustry.common.ModBlocks
import xyz.luobo.mindustry.common.ModItems
import xyz.luobo.mindustry.common.items.Materials

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

class ModLanguageProvider(output: PackOutput, locale: String): LanguageProvider(output, Mindustry.MOD_ID, locale) {
    override fun addTranslations() {
        this.add("itemGroup.mindustry", "Mindustry")
        this.add(ModBlocks.POWER_NODE_BLOCK.get(), "Power Node")
        this.add(ModItems.EXAMPLE_ITEM.get(), "Example Item")
    }
}

class ModItemModelProvider(output: PackOutput, existingFileHelper: ExistingFileHelper): ItemModelProvider(output, Mindustry.MOD_ID, existingFileHelper) {
    override fun registerModels() {
        this.basicItem(ModItems.EXAMPLE_ITEM.get())
        Materials.ALL.forEach { material ->
            this.basicItem(ModItems.getMaterial(material).get())
        }
    }
}

class ModBlockStateProvider(output: PackOutput, existingFileHelper: ExistingFileHelper): BlockStateProvider(output, Mindustry.MOD_ID, existingFileHelper) {
    override fun registerStatesAndModels() {
        this.simpleBlockWithItem(ModBlocks.POWER_NODE_BLOCK.get(), cubeAll(ModBlocks.POWER_NODE_BLOCK.get()))
    }
}