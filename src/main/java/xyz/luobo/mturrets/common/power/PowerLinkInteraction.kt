package xyz.luobo.mturrets.common.power

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.LogicalSide
import net.minecraft.world.InteractionHand
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import xyz.luobo.mturrets.core.power.PowerGraphs
import xyz.luobo.mturrets.core.power.PowerLinks

/**
 * 电力节点接线交互(ADR-0007 修订 #69,对位 Mindustry `onConfigureBuildTapped`):
 * 空手右键节点 = 点选接线——
 * - 无待选 → 第一击记 [pending](服务端)并镜像客户端高亮;
 * - 有待选(另一节点) → 对待选与当前点切换(有链拆、无链建)并清待选;
 * - 再次点击同一节点 → 待选为空则自动补满候选、已有链则清空(双击语义)。
 *
 * 服务端权威([pending]);客户端事件只镜像 [PowerLinkSelector.clientPending] 供激光范围圈,
 * 不参与拓扑。仅命中 PowerNodeBE 格,避免劫持窑炉/钻头等的空手取出交互。
 */
@EventBusSubscriber(modid = xyz.luobo.mturrets.MTurrets.MOD_ID)
object PowerLinkInteraction {

    /** 待选接线端点(每玩家/每服务端世界;服务端权威)。 */
    private val pending = HashMap<ServerLevel, HashMap<ServerPlayer, BlockPos>>()

    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        if (event.hand != InteractionHand.MAIN_HAND) return
        if (!event.itemStack.isEmpty) return // 仅空手接线
        val level = event.level
        if (level.getBlockEntity(event.pos) !is PowerNodeBE) return // 只处理节点,不劫持窑炉/钻头

        // 客户端镜像:第一击记待选高亮;服务端事件随后重置回放不处理
        if (event.side == LogicalSide.CLIENT) {
            xyz.luobo.mturrets.client.power.PowerLinkSelector.clientPending = event.pos
            return
        }

        val player = event.entity as? ServerPlayer ?: return
        if (player.isFakePlayer()) return
        val serverLevel = level as? ServerLevel ?: return

        val perWorld = pending.getOrPut(serverLevel) { HashMap() }
        val prev = perWorld[player]

        if (prev == null) {
            // 第一击:记待选并回显 actionbar
            perWorld[player] = event.pos
            player.connection.send(
                ClientboundSetActionBarTextPacket(Component.translatable("mturrets.message.link_select"))
            )
            event.setCanceled(true)
            return
        }
        if (prev == event.pos) {
            // 双击同一节点:无链 → 自动补满;有链 → 清空
            val node = serverLevel.getBlockEntity(event.pos) as? PowerNodeBE
            perWorld.remove(player)
            if (node != null) {
                if (node.links.isNotEmpty()) PowerLinks.reset(serverLevel, node) else node.autolink()
                PowerGraphs.recolorAround(serverLevel, node.blockPos)
            }
            event.setCanceled(true)
            return
        }

        // 待选为另一节点:切换链路;待选已失效则直接清待选
        val other = serverLevel.getBlockEntity(prev) as? PowerNodeBE
        val current = serverLevel.getBlockEntity(event.pos) as? PowerNodeBE
        perWorld.remove(player)
        if (other != null && current != null) {
            PowerLinks.toggle(serverLevel, other, current)
        }
        event.setCanceled(true)
    }

    /** 玩家下线清理其待选态。 */
    @EventBusSubscriber(modid = "mturrets")
    object Cleanup {
        @SubscribeEvent
        fun onLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
            val player = event.entity as? ServerPlayer ?: return
            for (world in pending.values) world.remove(player)
        }
    }
}