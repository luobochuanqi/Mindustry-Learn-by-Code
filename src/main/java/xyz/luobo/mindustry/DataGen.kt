package xyz.luobo.mindustry

import net.minecraft.core.HolderLookup
import net.minecraft.data.DataGenerator
import net.minecraft.data.PackOutput
import net.minecraft.data.recipes.RecipeProvider
import net.neoforged.neoforge.client.model.generators.BlockStateProvider
import net.neoforged.neoforge.client.model.generators.ItemModelProvider
import net.neoforged.neoforge.common.data.ExistingFileHelper
import net.neoforged.neoforge.common.data.LanguageProvider
import net.neoforged.neoforge.data.event.GatherDataEvent
import xyz.luobo.mindustry.common.ModBlocks
import xyz.luobo.mindustry.common.ModItems
import xyz.luobo.mindustry.common.items.Materials
import java.util.concurrent.CompletableFuture

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

class ModLanguageProvider(output: PackOutput, locale: String) : LanguageProvider(output, Mindustry.MOD_ID, locale) {
    override fun addTranslations() {
        // Item Group
        this.add("itemGroup.mindustry", "Mindustry")

        // Items
        this.add(ModItems.DEBUG_BACON.get(), "Debug Bacon")
        this.add(ModBlocks.POWER_NODE_BLOCK.get(), "Power Node")
        this.add(ModItems.EXAMPLE_ITEM.get(), "Example Item")
        Materials.ALL.forEach { material ->
            this.add(ModItems.getMaterial(material).get(), material.displayName)
        }

        // Configs
        this.add("mindustry.configuration.isDebugMode", "Debug Mode")
        this.add("mindustry.configuration.maxRenderDistance", "Max Laser Render Distance")
    }
}

class ModItemModelProvider(output: PackOutput, existingFileHelper: ExistingFileHelper) :
    ItemModelProvider(output, Mindustry.MOD_ID, existingFileHelper) {
    override fun registerModels() {
        this.basicItem(ModItems.EXAMPLE_ITEM.get())
        this.basicItem(ModItems.DEBUG_BACON.get())

        this.simpleBlockItem(ModBlocks.DUO_BLOCK.get())
        Materials.ALL.forEach { material ->
            this.basicItem(ModItems.getMaterial(material).get())
        }
    }
}

class ModBlockStateProvider(output: PackOutput, existingFileHelper: ExistingFileHelper) :
    BlockStateProvider(output, Mindustry.MOD_ID, existingFileHelper) {
    override fun registerStatesAndModels() {
        this.simpleBlockWithItem(ModBlocks.POWER_NODE_BLOCK.get(), cubeAll(ModBlocks.POWER_NODE_BLOCK.get()))
        this.simpleBlockWithItem(ModBlocks.KILN_BLOCK.get(), cubeAll(ModBlocks.KILN_BLOCK.get()))
//        this.simpleBlockWithItem(ModBlocks.DUO_BLOCK.get(), )
//        this.directionalBlock(ModBlocks.GRAPHITE_PRESS_BLOCK.get(),   )
    }
}

class ModRecipeProvider(output: PackOutput, provider: CompletableFuture<HolderLookup.Provider>) :
    RecipeProvider(output, provider)