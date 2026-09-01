package xyz.luobo.mturrets.common.power

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import xyz.luobo.mturrets.common.ModBlockEntityTypes
import xyz.luobo.mturrets.core.power.PowerAnchorBE

/**
 * 电源 BE(#49,调试用):常量 333,320 FE/t 生产入电网,无状态(生产是成员属性
 * [productionPerTick],结算点聚合;非事件流)。走既有 PowerAnchorBE 管线(零物品/液体
 * capability,拆机只掉控制器物品)。非 Generator 家族(CONTEXT 词表):纯调试供能,不消耗燃料。
 */
class PowerSourceBE(pos: BlockPos, state: BlockState) :
    PowerAnchorBE(ModBlockEntityTypes.POWER_SOURCE.get(), pos, state) {

    companion object {
        /** 常量生产量:上游 powerProduction = 1_000_000/60 power/t(整型截断 16,666)× 20 FE
         * = 333,320 FE/t。Int 精确值,与 PowerGraph 全 Int 账本一致,GameTest 断言用此数。 */
        const val PRODUCTION_PER_TICK = 333_320
    }

    override val productionPerTick: Int get() = PRODUCTION_PER_TICK

    /** 服务端每 tick 触发所在图结算(生产者视角的结算入口);无图(未入网)时 no-op。 */
    fun tickServer() {
        val lv = level ?: return
        graph?.onProduce(lv.gameTime)
    }
}
