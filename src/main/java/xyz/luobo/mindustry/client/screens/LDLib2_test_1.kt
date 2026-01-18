package xyz.luobo.mindustry.client.screens

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import org.appliedenergistics.yoga.YogaFlexDirection
import java.util.concurrent.atomic.AtomicInteger

object LDLib2_test_1 {
    var valueHolder = AtomicInteger(0)

    fun createModularUI(player: Player): ModularUI {
        val root = UIElement().setId("root")

        // logo
        val image: UIElement = UIElement().setId("image")

        // buttons
        val buttons: UIElement = UIElement().layout {
            it.flexDirection(YogaFlexDirection.ROW)
        }.addChildren(
            Button().setText("-90°")
                .setOnClick { image.transform { it.rotation(it.rotation() - 90f) } },
            UIElement().layout { it.flex(1f) },
            Button().setText("+")
                .setOnClick {
                    if (valueHolder.get() < 100) {
                        valueHolder.incrementAndGet()
                    }
                },
            UIElement().layout { it.flex(1f) },
            Button().setText("+90°")
                .setOnClick { image.transform { it.rotation(it.rotation() + 90f) } }
        )

        val stylesheetSelector: UIElement = Selector<ResourceLocation>()
            .setSelected(StylesheetManager.GDP, false)
            .setCandidates(StylesheetManager.INSTANCE.getAllPackStylesheets().toList())
            .setOnValueChanged { selected ->
                // switch to the selected stylesheet
                val mui = root.modularUI
                if (mui != null) {
                    mui.styleEngine.clearAllStylesheets()
                    mui.styleEngine.addStylesheet(StylesheetManager.INSTANCE.getStylesheetSafe(selected))
                }
            }

        val bindNumber: UIElement = TextField()
            .setNumbersOnlyInt(0, 100)
            .setValue(valueHolder.get().toString())
            .bindObserver { value -> valueHolder.set(Integer.parseInt(value)) }
            .bindDataSource(SupplierDataSource.of { valueHolder.get().toString() })

        val playerInventory: UIElement = InventorySlots().setId("inv")

        root.addChildren(
            Label().setText("About Mindustry Mod"),
            image,
            buttons,
            bindNumber,
            playerInventory
        )

        val lss = """
            #root {
                background: built-in(ui-gdp:BORDER);
                padding-all: 7;
                gap-all: 5;
            }
            
            #image {
                width: 200;
                height: 28;
                background: sprite(mindustry:textures/ui/logo.png);
            }
            
            #inv {
                align-self: center;
            }
            
            #root label {
                horizontal-align: center;
            }
        """.trimIndent()

        val stylesheet = Stylesheet.parse(lss)
        val ui = UI.of(root, stylesheet)
        return ModularUI.of(ui, player)
    }
}