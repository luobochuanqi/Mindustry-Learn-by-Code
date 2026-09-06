package xyz.luobo.mturrets.client.power

import net.minecraft.client.Minecraft
import xyz.luobo.mturrets.core.power.PowerMemberBE

/**
 * 客户端电力节点枚举(ADR-0007 修订 #69):节点经 [PowerNodeRenderer] 的 BER 进
 * renderableBlockEntities,故 `LevelRenderer.iterateVisibleBlockEntities` 可枚举
 * (与 TurretDebug #77 同款结论)。[PowerLaserRenderer] 借此取视野内在役节点画激光。
 */
object PowerLinkSelector {
    /** 当前客户端待选端点(纯镜像,由 [PowerLinkInteraction] 客户端侧置位);供高亮。 */
    @Volatile
    var clientPending: net.minecraft.core.BlockPos? = null

    /** 枚举视野内所有在役 [PowerMemberBE](含节点/电池/发电机;激光只对有链的画)。 */
    fun allNodes(): List<PowerMemberBE> {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return emptyList()
        val out = ArrayList<PowerMemberBE>()
        mc.levelRenderer.iterateVisibleBlockEntities { be ->
            if (be is PowerMemberBE) out += be
        }
        return out
    }
}