package xyz.luobo.mindustry

import net.minecraft.data.DataGenerator
import net.minecraft.data.PackOutput
import net.neoforged.neoforge.client.model.generators.ItemModelProvider
import net.neoforged.neoforge.common.data.ExistingFileHelper
import net.neoforged.neoforge.common.data.LanguageProvider
import net.neoforged.neoforge.data.event.GatherDataEvent
import xyz.luobo.mindustry.Common.ModBlocks
import xyz.luobo.mindustry.Common.ModItems

object DataGen {
    fun generate(event: GatherDataEvent) {
        val generator: DataGenerator = event.generator
        val packOutput = generator.packOutput

        generator.addProvider(event.includeClient(), ModLanguageProvider(packOutput, "en_us"))
        generator.addProvider(event.includeClient(), ModItemModelProvider(packOutput, event.existingFileHelper))
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
    }
}