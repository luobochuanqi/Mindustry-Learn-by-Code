package xyz.luobo.mindustry.common.items

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import xyz.luobo.mindustry.Mindustry
import xyz.luobo.mindustry.common.screen.LDLib2_test_1.createModularUI


class DebugBaconItem(properties: Properties) : Item(
    Properties()
        .stacksTo(1)
        .rarity(Rarity.EPIC)
) {
    override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack?> {
        return super.use(level, player, usedHand)
    }

    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        if (level.isClientSide) {
            Mindustry.LOGGER.debug("Start to create test UI...")
            val modularUI: ModularUI = createModularUI()
            Minecraft.getInstance().setScreen(ModularUIScreen(modularUI, Component.empty()))
        }
        return InteractionResult.SUCCESS
    }
}