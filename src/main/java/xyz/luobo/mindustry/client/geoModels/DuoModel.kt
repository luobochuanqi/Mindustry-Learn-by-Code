package xyz.luobo.mindustry.client.geoModels

import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel
import xyz.luobo.mindustry.Mindustry
import xyz.luobo.mindustry.common.turrets.duo.DuoBE

class DuoModel : GeoModel<DuoBE>() {
    val model = ResourceLocation.fromNamespaceAndPath(Mindustry.MOD_ID, "geo/duo.geo.json")
    val texture = ResourceLocation.fromNamespaceAndPath(Mindustry.MOD_ID, "textures/block/duo.png")
    val animations = ResourceLocation.fromNamespaceAndPath(Mindustry.MOD_ID, "animations/duo.animation.json")

    override fun getModelResource(animatable: DuoBE?): ResourceLocation? {
        return model
    }

    override fun getTextureResource(animatable: DuoBE?): ResourceLocation? {
        return texture
    }

    override fun getAnimationResource(animatable: DuoBE?): ResourceLocation? {
        return animations
    }
}