package xyz.luobo.mindustry.common.screen

import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites
import org.appliedenergistics.yoga.YogaFlexDirection

object LDLib2_test_1 {
    public fun createModularUI(): ModularUI {
        val root = UIElement();
        // logo
        val image: UIElement = UIElement().layout { layoutStyle ->
            layoutStyle.width(200f).height(50f)
        }.style { style ->
            style.background(SpriteTexture.of("mindustry:textures/ui/logo.png"))
        }
        // buttons
        val buttons: UIElement = UIElement().layout { layoutStyle ->
            layoutStyle.flexDirection(YogaFlexDirection.ROW)
        }.addChildren(
            Button().setText("+90°")
                .setOnClick { event -> image.transform { transform2D -> transform2D.rotation(transform2D.rotation() + 90f) } },
            UIElement().layout { layoutStyle -> layoutStyle.flex(1f) },
            Button().setText("-90°")
                .setOnClick { event -> image.transform { transform2D -> transform2D.rotation(transform2D.rotation() - 90f) } },
            UIElement().layout { layoutStyle -> layoutStyle.flex(1f) },
            Button().setText("QwQ")
        )

        root.addChildren(
            Label().setText("Hello, LDLib2!")
                .textStyle { textStyle -> textStyle.textAlignHorizontal(Horizontal.CENTER) },
            image,
//            Button().setText("Why not try?"),
            buttons
//            UIElement().layout { it.width(80f).height(80f) }
//                .style {
//                    it.background(SpriteTexture.of("ldlib2:textures/gui/icon.png"))
//                }
        ).style { it.background(Sprites.BORDER) }

        root.layout { layoutStyle -> layoutStyle.paddingAll(7f).gapAll(5f) }

        val ui = UI.of(root)

        return ModularUI.of(ui)
    }
}