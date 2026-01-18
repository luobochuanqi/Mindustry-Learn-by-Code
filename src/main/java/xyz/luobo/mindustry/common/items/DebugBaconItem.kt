package xyz.luobo.mindustry.common.items

import com.lowdragmc.lowdraglib2.gui.factory.HeldItemUIMenuType
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity
import net.minecraft.world.level.Level
import xyz.luobo.mindustry.client.screens.LDLib2_test_1.createModularUI


class DebugBaconItem(properties: Properties) : Item(
    Properties()
        .stacksTo(1)
        .rarity(Rarity.EPIC)
), HeldItemUIMenuType.HeldItemUI {
    override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack?> {
//        if (level.isClientSide) {
//            Mindustry.LOGGER.debug("Start to create test UI...")
//            val modularUI: ModularUI = createModularUI(player)
//            val menu: ModularUIContainerMenu = ModularUIContainerMenu
//            Minecraft.getInstance().setScreen(ModularUIContainerScreen(modularUI, player.inventory, Component.empty()))
//        }
        if (player is ServerPlayer) {
            HeldItemUIMenuType.openUI(player, usedHand)
        }
        return super.use(level, player, usedHand)
    }

    override fun createUI(holder: HeldItemUIMenuType.HeldItemUIHolder?): ModularUI {
        return createModularUI(holder!!.player)
    }
}