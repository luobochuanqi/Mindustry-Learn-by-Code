package xyz.luobo.mindustry.common.turrets.duo

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import software.bernie.geckolib.animatable.GeoBlockEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil
import xyz.luobo.mindustry.common.ModBlockEntityTypes

class DuoBE(
    pos: BlockPos,
    blockState: BlockState
) : BlockEntity(ModBlockEntityTypes.DUO_Block_Entity.get(), pos, blockState), GeoBlockEntity {
    private val animatableInstanceCache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)
    val DEPLOY_ANIM: RawAnimation = RawAnimation.begin().thenPlay("animation")

    // 返回动画控制器/
    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(AnimationController(this) { state ->
            state.setAndContinue(DEPLOY_ANIM)
        })
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache {
        return animatableInstanceCache
    }
}