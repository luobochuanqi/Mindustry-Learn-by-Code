package xyz.luobo.mindustry.client.screens

import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites
import org.appliedenergistics.yoga.YogaFlexDirection

object LDLib2_test_1 {
    fun createModularUI(): ModularUI {
        val root = UIElement().setId("root")
        // logo
        val image: UIElement = UIElement().layout {
            it.width(200f).height(80f)
        }.style {
            it.background(SpriteTexture.of("mindustry:textures/ui/logo.png"))
        }.addClass("image")
        // buttons
        val buttons: UIElement = UIElement().layout {
            it.flexDirection(YogaFlexDirection.ROW)
        }.addChildren(
            Button().setText("+90°")
                .setOnClick { _ -> image.transform { it.rotation(it.rotation() + 90f) } },
            UIElement().layout { it.flex(1f) },
            Button().setText("-90°")
                .setOnClick { _ -> image.transform { it.rotation(it.rotation() - 90f) } },
            UIElement().layout { it.flex(1f) },
            Button().setText("QwQ")
        )

        root.addChildren(
            Label().setText("Hello, LDLib2!")
                .textStyle { textStyle -> textStyle.textAlignHorizontal(Horizontal.CENTER) },
            image,
            buttons
        ).style { it.background(Sprites.BORDER) }

        val lss = """
            #root {
                background: built-in(ui-gdp:BORDER);
                padding-all: 7;
                gap-all: 5;
            }
            
            // class selector
            .image {
                width: 80;
                height: 80;
                background: sprite(ldlib2:textures/gui/icon.png);
            }
    
            // element selector
            #root label {
                horizontal-align: center;
            }
        """.trimIndent()

        val stylesheet = Stylesheet.parse(lss)
        val ui = UI.of(root, stylesheet)
        return ModularUI.of(ui)
    }
}